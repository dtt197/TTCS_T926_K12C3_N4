package com.ttcs.homestay.exception;

public class RoomTypeUnavailableException extends RuntimeException {

    public RoomTypeUnavailableException() {
        super("Loại phòng này hiện đã ngừng bán");
    }
}
