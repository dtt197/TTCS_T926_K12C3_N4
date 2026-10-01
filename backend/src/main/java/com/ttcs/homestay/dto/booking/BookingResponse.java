package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.Booking;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BookingResponse(
        Long id,
        Long roomTypeId,
        String roomTypeName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        long weekdayPriceSnapshot,
        long weekendPriceSnapshot,
        String weekendDaysSnapshot,
        long totalAmount,
        OffsetDateTime createdAt) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getRoomType() == null ? null : booking.getRoomType().getId(),
                booking.getRoomTypeNameSnapshot(),
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                booking.getWeekdayPriceSnapshot(),
                booking.getWeekendPriceSnapshot(),
                booking.getWeekendDaysSnapshot(),
                booking.getTotalAmount(),
                booking.getCreatedAt());
    }
}