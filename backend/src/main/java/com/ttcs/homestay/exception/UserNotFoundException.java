package com.ttcs.homestay.exception;

public class UserNotFoundException extends RuntimeException {

	public UserNotFoundException() {
		super("Không tìm thấy tài khoản");
	}
}