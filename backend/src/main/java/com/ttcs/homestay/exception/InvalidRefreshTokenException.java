package com.ttcs.homestay.exception;

public class InvalidRefreshTokenException extends RuntimeException {

	public InvalidRefreshTokenException() {
		super("Refresh token không hợp lệ hoặc đã hết hạn");
	}
}