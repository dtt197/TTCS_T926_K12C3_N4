package com.ttcs.homestay.service;

import java.time.LocalDate;
import java.time.ZoneId;

public final class PublicBookingDatePolicy {

    public static final int MAX_ADVANCE_MONTHS = 12;
    public static final String CHECK_IN_TOO_FAR_MESSAGE =
            "Ngày nhận phòng không được quá 12 tháng kể từ ngày hiện tại.";

    private static final ZoneId HOMESTAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private PublicBookingDatePolicy() {
    }

    public static LocalDate today() {
        return LocalDate.now(HOMESTAY_ZONE);
    }

    public static LocalDate maximumCheckInDate(LocalDate today) {
        return today.plusMonths(MAX_ADVANCE_MONTHS);
    }

    public static boolean isCheckInTooFar(LocalDate checkIn, LocalDate today) {
        return checkIn.isAfter(maximumCheckInDate(today));
    }
}
