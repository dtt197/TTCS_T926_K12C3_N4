package com.ttcs.homestay.exception;

public class ResendPasswordNotAllowedException extends RuntimeException {

	public ResendPasswordNotAllowedException() {
		super("Tài khoản đã tự đổi mật khẩu, không cần gửi lại mật khẩu tạm");
	}
}