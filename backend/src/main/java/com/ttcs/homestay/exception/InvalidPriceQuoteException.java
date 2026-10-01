package com.ttcs.homestay.exception;

/** S2-02: khoảng ngày tính giá không hợp lệ, hoặc loại phòng thiếu giá cần dùng. */
public class InvalidPriceQuoteException extends RuntimeException {

    public InvalidPriceQuoteException(String message) {
        super(message);
    }
}