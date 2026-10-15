package com.ttcs.homestay.exception;

public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException() {
        super("Không tìm thấy thông báo");
    }
}
