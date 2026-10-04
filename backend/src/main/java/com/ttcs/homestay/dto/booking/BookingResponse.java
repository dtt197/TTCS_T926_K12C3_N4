package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.service.BookingHoldPolicy;
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
    OffsetDateTime createdAt,
    boolean holdExpired,
    String guestPhone
) {
    public BookingResponse(
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
        OffsetDateTime createdAt,
        boolean holdExpired
    ) {
        this(id, bookingCode, guestName, roomTypeId, roomTypeNameSnapshot, checkInDate, checkOutDate,
             weekdayPriceSnapshot, weekendPriceSnapshot, weekendDaysSnapshot, totalAmount, status, createdAt, holdExpired, null);
    }

    public static BookingResponse from(Booking booking) {
        boolean expired = booking.getStatus() == BookingStatus.CHO_XAC_NHAN && (
            (booking.getHoldExpiresAt() != null && booking.getHoldExpiresAt().toInstant().isBefore(java.time.Instant.now()))
            || BookingHoldPolicy.isExpired(
                booking.getStatus(), 
                booking.getCreatedAt() != null ? booking.getCreatedAt().toInstant() : null, 
                java.time.Instant.now()
            )
        );

        return new BookingResponse(
            booking.getId(),
            booking.getBookingCode(),
            booking.getGuestName(),
            booking.getRoomType() != null ? booking.getRoomType().getId() : null,
            booking.getRoomTypeNameSnapshot(),
            booking.getCheckInDate(),
            booking.getCheckOutDate(),
            booking.getWeekdayPriceSnapshot(),
            booking.getWeekendPriceSnapshot(),
            booking.getWeekendDaysSnapshot(),
            booking.getTotalAmount(),
            booking.getStatus(),
            booking.getCreatedAt(),
            expired,
            booking.getGuestPhone()
        );
    }
}