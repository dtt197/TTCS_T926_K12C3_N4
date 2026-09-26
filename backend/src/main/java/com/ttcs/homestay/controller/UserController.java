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

import jakarta.validation.Valid;

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
}