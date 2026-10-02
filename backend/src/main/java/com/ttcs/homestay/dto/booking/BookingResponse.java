package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.Booking; // Nhớ import entity Booking nếu chưa có
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
    // Thêm phương thức from này vào để ánh xạ từ Entity sang DTO
    public static BookingResponse from(Booking booking) {
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
            booking.getCreatedAt()
        );
    }
}