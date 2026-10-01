package com.ttcs.homestay.exception;

public class PriceOverrideNotFoundException extends RuntimeException {

    public PriceOverrideNotFoundException() {
        super("Không tìm thấy đợt giá đè");
    }
}