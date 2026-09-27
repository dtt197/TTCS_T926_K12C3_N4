package com.ttcs.homestay.exception;

public class InvalidResetTokenException extends RuntimeException {
	public InvalidResetTokenException() {
		super("Liên kết đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
	}
}
