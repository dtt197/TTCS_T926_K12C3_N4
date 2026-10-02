package com.ttcs.homestay.exception;

/** S2-07: loại phòng đã hết phòng trống trong khoảng ngày khách chọn. */
public class RoomUnavailableException extends RuntimeException {

    public RoomUnavailableException(String message) {
        super(message);
    }
}