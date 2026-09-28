package com.ttcs.homestay.exception;

public class AmenityNotFoundException extends RuntimeException {

    public AmenityNotFoundException() {
        super("Không tìm thấy tiện nghi");
    }
}