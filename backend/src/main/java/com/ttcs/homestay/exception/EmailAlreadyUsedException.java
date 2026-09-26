package com.ttcs.homestay.exception;

public class EmailAlreadyUsedException extends RuntimeException {

	public EmailAlreadyUsedException(String email) {
		super("Email " + email + " đã được dùng cho một tài khoản khác");
	}
}