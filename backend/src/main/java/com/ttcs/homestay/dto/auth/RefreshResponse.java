package com.ttcs.homestay.dto.auth;

public record RefreshResponse(
		String accessToken,
		long expiresIn
) {
}
