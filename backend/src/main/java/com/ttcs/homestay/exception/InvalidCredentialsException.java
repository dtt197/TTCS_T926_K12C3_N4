package com.ttcs.homestay.exception;

public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("Email hoặc mật khẩu không chính xác");
	}
}