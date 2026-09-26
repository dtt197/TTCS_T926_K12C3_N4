package com.ttcs.homestay.dto.auth;

public record InternalUserResponse(
		Long userId,
		String fullName,
		String email,
		String role,
		boolean mustChangePassword
) {
}