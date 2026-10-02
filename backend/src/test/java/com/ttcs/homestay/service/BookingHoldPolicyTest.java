package com.ttcs.homestay.service;

import static org.junit.jupiter.api.Assertions.*;

import com.ttcs.homestay.entity.BookingStatus;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BookingHoldPolicyTest {

    // Đổi REPLACE_ME thành đúng tên trạng thái "chờ xác nhận" trong BookingStatus
    // (giống tên đang dùng trong BookingHoldPolicy.java)
    private static final BookingStatus PENDING = BookingStatus.CHO_XAC_NHAN;

    private static final Instant NOW = Instant.parse("2026-10-10T10:00:00Z");

    @Test
    void pendingOver24h_isExpired() {
        assertTrue(BookingHoldPolicy.isExpired(
                PENDING, NOW.minus(Duration.ofHours(25)), NOW));
    }

    @Test
    void pendingUnder24h_isNotExpired() {
        assertFalse(BookingHoldPolicy.isExpired(
                PENDING, NOW.minus(Duration.ofHours(23)), NOW));
    }

    @Test
    void pendingExactly24h_isNotExpired() {
        assertFalse(BookingHoldPolicy.isExpired(
                PENDING, NOW.minus(Duration.ofHours(24)), NOW));
    }

    @Test
    void otherStatusesOver24h_areNeverExpired() {
        for (BookingStatus status : BookingStatus.values()) {
            if (status == PENDING) continue;
            assertFalse(
                    BookingHoldPolicy.isExpired(status, NOW.minus(Duration.ofHours(48)), NOW),
                    "Trạng thái " + status + " không được báo quá hạn");
        }
    }
}