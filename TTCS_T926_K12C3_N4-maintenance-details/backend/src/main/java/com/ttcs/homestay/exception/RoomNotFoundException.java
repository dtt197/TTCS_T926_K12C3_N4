package com.ttcs.homestay.exception;

public class RoomNotFoundException extends RuntimeException {
    public RoomNotFoundException(Long roomId) {
        super("Không tìm thấy phòng có mã " + roomId);
    }
}