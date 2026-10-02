package com.ttcs.homestay.exception;

/** S2-07: thông tin đặt phòng của khách không hợp lệ (ngày, loại phòng, số khách). */
public class InvalidGuestBookingException extends RuntimeException {

    public InvalidGuestBookingException(String message) {
        super(message);
    }
}