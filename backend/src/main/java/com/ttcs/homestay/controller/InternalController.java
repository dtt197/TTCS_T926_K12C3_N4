package com.ttcs.homestay.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ttcs.homestay.dto.auth.InternalUserResponse;
import com.ttcs.homestay.repository.UserRepository;

@RestController
@RequestMapping("/api/internal")
public class InternalController {

	private final UserRepository userRepository;

	public InternalController(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	/** Đọc thông tin mới nhất từ database (vd đã đổi mật khẩu tạm chưa), không chỉ dựa vào token. */
	@GetMapping("/me")
	public ResponseEntity<InternalUserResponse> me(@AuthenticationPrincipal Jwt jwt) {
		return userRepository.findWithRoleById(Long.valueOf(jwt.getSubject()))
				.map(user -> ResponseEntity.ok(new InternalUserResponse(
						user.getId(),
						user.getFullName(),
						user.getEmail(),
						user.getRole().getCode(),
						user.isMustChangePassword())))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
	}
}