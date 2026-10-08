package com.ttcs.homestay.exception;

@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.BAD_REQUEST)
public class InvalidDepositException extends RuntimeException {
    public InvalidDepositException() {
        super("Thông tin tiền cọc không hợp lệ");
    }

    public InvalidDepositException(String message) {
        super(message);
    }
}
