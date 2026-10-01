package com.ttcs.homestay.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * S2-07: mã booking 8 ký tự gồm chữ in hoa và số, bỏ 0/O và 1/I cho khách dễ đọc và gõ lại khi tra cứu (S2-08).
 * 32 ký tự ^ 8 ≈ 1.000 tỷ mã; việc chống trùng do GuestBookingService kiểm tra.
 */
@Component
public class BookingCodeGenerator {

    public static final int CODE_LENGTH = 8;

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder code = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}