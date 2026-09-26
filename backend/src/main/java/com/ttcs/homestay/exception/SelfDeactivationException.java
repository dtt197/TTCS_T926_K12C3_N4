package com.ttcs.homestay.exception;

public class SelfDeactivationException extends RuntimeException {

	public SelfDeactivationException() {
		super("Bạn không thể tự vô hiệu hoá tài khoản của chính mình");
	}
}