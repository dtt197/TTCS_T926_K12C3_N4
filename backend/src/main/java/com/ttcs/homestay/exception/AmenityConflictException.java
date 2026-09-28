package com.ttcs.homestay.exception;

/** S1-08: trùng mã tiện nghi, hoặc xoá tiện nghi đang được gắn cho loại phòng (AC3). */
public class AmenityConflictException extends RuntimeException {

    public AmenityConflictException(String message) {
        super(message);
    }
}