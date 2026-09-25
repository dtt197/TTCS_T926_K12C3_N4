package com.ttcs.homestay.exception;

public class RoomStatusConflictException extends RuntimeException {
    public RoomStatusConflictException(String message) {
        super(message);
    }
}