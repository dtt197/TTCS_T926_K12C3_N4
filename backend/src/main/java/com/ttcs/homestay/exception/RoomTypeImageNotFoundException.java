package com.ttcs.homestay.exception;

public class RoomTypeImageNotFoundException extends RuntimeException {

    public RoomTypeImageNotFoundException() {
        super("Không tìm thấy ảnh của loại phòng");
    }

    public RoomTypeImageNotFoundException(String message) {
        super(message);
    }
}
