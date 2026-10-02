package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.service.BookingHoldPolicy; // Import policy kiểm tra quá hạn
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
    boolean holdExpired // Thêm trường kiểm tra quá hạn
) {
    // Phương thức ánh xạ từ Entity sang DTO kèm logic tính holdExpired
    public static BookingResponse from(Booking booking) {
        boolean expired = BookingHoldPolicy.isExpired(
            booking.getStatus(), 
            booking.getCreatedAt() != null ? booking.getCreatedAt().toInstant() : null, 
            java.time.Instant.now()
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
            expired
        );
    }
}