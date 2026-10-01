package com.ttcs.homestay.exception;

/** S2-02 Lát 3: đợt giá đè trùng ngày với một đợt khác của cùng loại phòng. */
public class PriceOverrideConflictException extends RuntimeException {

    public PriceOverrideConflictException(String message) {
        super(message);
    }
}