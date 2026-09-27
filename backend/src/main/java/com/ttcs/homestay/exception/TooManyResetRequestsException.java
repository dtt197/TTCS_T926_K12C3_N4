package com.ttcs.homestay.exception;

public class TooManyResetRequestsException extends RuntimeException {
	public TooManyResetRequestsException() {
		super("Bạn đã yêu cầu đặt lại mật khẩu quá nhiều lần. Vui lòng thử lại sau");
	}
} 
