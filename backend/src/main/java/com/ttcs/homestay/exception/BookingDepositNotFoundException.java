package com.ttcs.homestay.exception;

public class BookingDepositNotFoundException extends RuntimeException {
    public static final String MESSAGE = "Không tìm thấy tiền cọc";

    public BookingDepositNotFoundException() {
        super(MESSAGE);
    }

    public BookingDepositNotFoundException(String message) {
        super(message);
    }
}
