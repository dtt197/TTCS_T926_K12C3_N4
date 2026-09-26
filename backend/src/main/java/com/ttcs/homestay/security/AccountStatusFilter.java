package com.ttcs.homestay.security;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Chạy sau khi xác thực token, đọc tài khoản mới nhất từ database:
 *  - S1-02 AC5: tài khoản bị vô hiệu hoá → phiên hiện tại hết hiệu lực ngay (401).
 *  - S1-02 AC4: còn dùng mật khẩu tạm → chỉ được đổi mật khẩu, các API khác bị chặn (403).
 */
public class AccountStatusFilter extends OncePerRequestFilter {

	private static final Set<String> ALLOWED_WHILE_MUST_CHANGE = Set.of(
			"/api/account/change-password",
			"/api/internal/me");

	private final UserRepository userRepository;

	public AccountStatusFilter(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
			Long userId = Long.valueOf(jwtAuthentication.getToken().getSubject());
			Optional<User> user = userRepository.findById(userId);

			if (user.isEmpty() || !user.get().isActive()) {
				writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "ACCOUNT_DISABLED",
						"Tài khoản đã bị vô hiệu hoá. Vui lòng liên hệ quản trị viên");
				return;
			}

			String path = request.getRequestURI();
			if (user.get().isMustChangePassword()
					&& !path.startsWith("/api/auth/")
					&& !ALLOWED_WHILE_MUST_CHANGE.contains(path)) {
				writeError(response, HttpServletResponse.SC_FORBIDDEN, "PASSWORD_CHANGE_REQUIRED",
						"Bạn cần đổi mật khẩu tạm trước khi sử dụng hệ thống");
				return;
			}
		}
		chain.doFilter(request, response);
	}

	private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
		response.setStatus(status);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
	}
}