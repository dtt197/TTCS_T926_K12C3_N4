package com.ttcs.homestay.exception;

public class SettingsNotFoundException extends RuntimeException {

    public SettingsNotFoundException() {
        super("Chưa khai báo tham số vận hành");
    }
}