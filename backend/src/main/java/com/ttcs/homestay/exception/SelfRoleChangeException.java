package com.ttcs.homestay.exception;

public class SelfRoleChangeException extends RuntimeException {

	public SelfRoleChangeException() {
		super("Bạn không thể tự đổi vai trò của chính mình");
	}
}