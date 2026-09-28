package com.ttcs.homestay.exception;

/** S1-06: trùng mã (AC3), trùng tên, hoặc xoá loại phòng đang có phòng gắn vào (AC4). */
public class RoomTypeConflictException extends RuntimeException {

    public RoomTypeConflictException(String message) {
        super(message);
    }
}