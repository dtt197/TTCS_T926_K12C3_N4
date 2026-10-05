package com.ttcs.homestay.exception;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * S2-08 Lát 2: IP đã tra cứu sai 10 lần trong 15 phút.
 * Thông báo nêu giờ được thử lại theo dd/MM/yyyy HH:mm, múi giờ Asia/Ho_Chi_Minh,
 * làm tròn lên phút kế tiếp để khách thử lại đúng giờ ghi là được.
 */
public class BookingLookupLimitException extends RuntimeException {

    private static final DateTimeFormatter RETRY_FORMAT = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final Instant retryAt;

    public BookingLookupLimitException(Instant blockedUntil) {
        super(message(roundUpToMinute(blockedUntil)));
        this.retryAt = roundUpToMinute(blockedUntil);
    }

    public Instant getRetryAt() {
        return retryAt;
    }

    private static String message(Instant retryAt) {
        return "Bạn đã nhập sai mã booking hoặc email 10 lần trong 15 phút. Vui lòng thử lại sau "
                + RETRY_FORMAT.format(retryAt) + ".";
    }

    private static Instant roundUpToMinute(Instant instant) {
        Instant truncated = instant.truncatedTo(ChronoUnit.MINUTES);
        return truncated.equals(instant) ? truncated : truncated.plus(1, ChronoUnit.MINUTES);
    }
}