package com.ttcs.homestay.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ttcs.homestay.dto.user.CreateUserRequest;
import com.ttcs.homestay.dto.user.UserResponse;
import com.ttcs.homestay.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.ttcs.homestay.dto.user.UpdateUserStatusRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PutMapping;
import com.ttcs.homestay.dto.user.UpdateUserRequest;

/** S1-02: quản trị hệ thống quản lý tài khoản nhân viên. Chỉ vai trò ADMIN gọi được (xem SecurityConfig). */
@RestController
@RequestMapping("/api/admin/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@GetMapping
	public List<UserResponse> listUsers() {
		return userService.listUsers();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse createUser(@Valid @RequestBody CreateUserRequest request) {
		return userService.createUser(request);
	}
	@PatchMapping("/{id}/status")
	public UserResponse updateStatus(@PathVariable Long id,
			@Valid @RequestBody UpdateUserStatusRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return userService.updateStatus(id, request, Long.valueOf(jwt.getSubject()));
	}
	@PutMapping("/{id}")
	public UserResponse updateUser(@PathVariable Long id,
			@Valid @RequestBody UpdateUserRequest request,
			@AuthenticationPrincipal Jwt jwt) {
		return userService.updateUser(id, request, Long.valueOf(jwt.getSubject()));
	}

	@PostMapping("/{id}/resend-temporary-password")
	public UserResponse resendTemporaryPassword(@PathVariable Long id) {
		return userService.resendTemporaryPassword(id);
	}
}