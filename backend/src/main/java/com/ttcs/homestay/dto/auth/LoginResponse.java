package com.ttcs.homestay.dto.auth;

public record LoginResponse(
		Long userId,
		String fullName,
		String email,
		String role,
		String accessToken,
		long expiresIn,
		boolean mustChangePassword
) {

	/** Giữ cách tạo cũ 6 tham số (test của S1-01 đang dùng), mặc định không phải đổi mật khẩu. */
	public LoginResponse(Long userId, String fullName, String email, String role, String accessToken, long expiresIn) {
		this(userId, fullName, email, role, accessToken, expiresIn, false);
	}
}