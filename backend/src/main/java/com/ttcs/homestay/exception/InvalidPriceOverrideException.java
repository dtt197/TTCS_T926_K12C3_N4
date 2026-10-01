package com.ttcs.homestay.exception;

/** S2-02: ngày kết thúc trước ngày bắt đầu, đợt quá dài, hoặc loại phòng không tồn tại. */
public class InvalidPriceOverrideException extends RuntimeException {

    public InvalidPriceOverrideException(String message) {
        super(message);
    }
}