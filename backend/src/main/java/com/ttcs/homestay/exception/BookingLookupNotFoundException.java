package com.ttcs.homestay.exception;

/**
 * S2-08: mã booking không tồn tại, email không khớp hoặc cả hai đều sai.
 * Mọi trường hợp dùng chung một thông báo để không lộ mã booking nào có thật.
 */
public class BookingLookupNotFoundException extends RuntimeException {

    public static final String MESSAGE =
            "Mã booking hoặc email không đúng. Vui lòng kiểm tra lại.";

    public BookingLookupNotFoundException() {
        super(MESSAGE);
    }
}