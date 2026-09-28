package com.ttcs.homestay.exception;

public class RoomTypeNotFoundException extends RuntimeException {

    public RoomTypeNotFoundException() {
        super("Không tìm thấy loại phòng");
    }
}