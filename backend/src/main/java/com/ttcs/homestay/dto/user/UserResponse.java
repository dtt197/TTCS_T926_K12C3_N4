package com.ttcs.homestay.dto.user;

import java.time.OffsetDateTime;

import com.ttcs.homestay.entity.User;

public record UserResponse(
		Long id,
		String fullName,
		String email,
		String phone,
		String role,
		boolean active,
		boolean mustChangePassword,
		OffsetDateTime createdAt) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
				user.getRole().getCode(), user.isActive(), user.isMustChangePassword(), user.getCreatedAt());
	}
}