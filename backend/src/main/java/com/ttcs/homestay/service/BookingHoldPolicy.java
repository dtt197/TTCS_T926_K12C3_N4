package com.ttcs.homestay.service;

import com.ttcs.homestay.entity.BookingStatus;
import java.time.Duration;
import java.time.Instant;

public final class BookingHoldPolicy {

    public static final Duration HOLD_DURATION = Duration.ofHours(24);

    private BookingHoldPolicy() {}

    /** Quá hạn khi đang chờ xác nhận và đã tạo hơn 24 giờ. */
    public static boolean isExpired(BookingStatus status, Instant createdAt, Instant now) {
        return status == BookingStatus.CHO_XAC_NHAN
                && createdAt != null
                && createdAt.plus(HOLD_DURATION).isBefore(now);
    }
}
