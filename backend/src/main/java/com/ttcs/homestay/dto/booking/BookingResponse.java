package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.BookingStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BookingResponse(
    Long id,
    String bookingCode,
    String guestName,
    Long roomTypeId,
    String roomTypeNameSnapshot,
    LocalDate checkInDate,
    LocalDate checkOutDate,
    Long weekdayPriceSnapshot,
    Long weekendPriceSnapshot,
    String weekendDaysSnapshot,
    Long totalAmount,
    BookingStatus status,
    OffsetDateTime createdAt
) {
}