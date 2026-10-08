package com.ttcs.homestay.exception;

@org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CONFLICT)
public class DuplicatePaymentException extends RuntimeException {
    public DuplicatePaymentException() {
        super("Đã có tiền cọc cho booking này");
    }

    public DuplicatePaymentException(String message) {
        super(message);
    }
}
