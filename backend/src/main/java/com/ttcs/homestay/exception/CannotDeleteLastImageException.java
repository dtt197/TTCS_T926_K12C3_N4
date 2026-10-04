package com.ttcs.homestay.exception;

public class CannotDeleteLastImageException extends RuntimeException {

    public CannotDeleteLastImageException() {
        super("Không được phép xoá ảnh cuối cùng của loại phòng đang mở bán.");
    }

    public CannotDeleteLastImageException(String message) {
        super(message);
    }
}
