package com.ttcs.homestay.exception;

/** S4-07: khoảng ngày thống kê doanh thu không hợp lệ. */
public class InvalidRevenueRangeException extends RuntimeException {

    public InvalidRevenueRangeException(String message) {
        super(message);
    }
}