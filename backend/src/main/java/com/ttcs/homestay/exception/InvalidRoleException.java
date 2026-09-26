package com.ttcs.homestay.exception;

public class InvalidRoleException extends RuntimeException {

	public InvalidRoleException(String role) {
		super("Vai trò không hợp lệ: " + role);
	}
}