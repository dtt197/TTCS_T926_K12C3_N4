package com.ttcs.homestay.dto.auth;

public record LoginResult(
		LoginResponse response,
		String refreshToken
) {
}
