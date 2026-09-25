package com.ttcs.homestay.dto.auth;

public record LoginResponse(
		Long userId,
		String fullName,
		String email,
		String role
) {
}