package com.ttcs.homestay.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.UserRepository;

/** S1-02 AC4 (bắt đổi mật khẩu tạm), AC5 (vô hiệu hoá làm hết hiệu lực phiên), Lát 4 (đổi vai trò → đăng nhập lại). */
class AccountStatusFilterTest {

	private final UserRepository userRepository = mock(UserRepository.class);
	private final AccountStatusFilter filter = new AccountStatusFilter(userRepository);

	@AfterEach
	void clearLogin() {
		SecurityContextHolder.clearContext();
	}

	/** Giả lập: trong database tài khoản có vai trò dbRole; token đăng nhập ghi vai trò tokenRole. */
	private void loginAs(long userId, boolean active, boolean mustChangePassword, String dbRole, String tokenRole) {
		Role role = mock(Role.class);
		when(role.getCode()).thenReturn(dbRole);
		User user = mock(User.class);
		when(user.isActive()).thenReturn(active);
		when(user.isMustChangePassword()).thenReturn(mustChangePassword);
		when(user.getRole()).thenReturn(role);
		when(userRepository.findWithRoleById(userId)).thenReturn(Optional.of(user));
		Jwt jwt = Jwt.withTokenValue("token").header("alg", "HS256")
				.subject(String.valueOf(userId)).claim("role", tokenRole).build();
		SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
	}

	private void loginAs(long userId, boolean active, boolean mustChangePassword) {
		loginAs(userId, active, mustChangePassword, "RECEPTIONIST", "RECEPTIONIST");
	}

	private MockFilterChain call(String path, MockHttpServletResponse response) throws Exception {
		MockFilterChain chain = new MockFilterChain();
		filter.doFilter(new MockHttpServletRequest("GET", path), response, chain);
		return chain;
	}

	@Test
	void taiKhoanBiVoHieuHoa_phienBiTuChoi401() throws Exception {
		loginAs(5L, false, false);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = call("/api/admin/users", response);
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(chain.getRequest()).isNull();
	}

	@Test
	void conMatKhauTam_goiApiNghiepVu_biChan403() throws Exception {
		loginAs(6L, true, true);
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = call("/api/admin/users", response);
		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentAsString()).contains("PASSWORD_CHANGE_REQUIRED");
		assertThat(chain.getRequest()).isNull();
	}

	@Test
	void conMatKhauTam_vanDuocGoiApiDoiMatKhau() throws Exception {
		loginAs(6L, true, true);
		MockFilterChain chain = call("/api/account/change-password", new MockHttpServletResponse());
		assertThat(chain.getRequest()).isNotNull();
	}

	@Test
	void vaiTroTrongTokenKhacDatabase_batDangNhapLai401() throws Exception {
		loginAs(8L, true, false, "HOUSEKEEPING", "RECEPTIONIST");
		MockHttpServletResponse response = new MockHttpServletResponse();
		MockFilterChain chain = call("/api/internal/me", response);
		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentAsString()).contains("ROLE_CHANGED");
		assertThat(chain.getRequest()).isNull();
	}

	@Test
	void taiKhoanBinhThuong_diQua() throws Exception {
		loginAs(7L, true, false);
		MockFilterChain chain = call("/api/admin/users", new MockHttpServletResponse());
		assertThat(chain.getRequest()).isNotNull();
	}

	@Test
	void chuaDangNhap_khongKiemTra_diQua() throws Exception {
		MockFilterChain chain = call("/api/rooms", new MockHttpServletResponse());
		assertThat(chain.getRequest()).isNotNull();
	}
}