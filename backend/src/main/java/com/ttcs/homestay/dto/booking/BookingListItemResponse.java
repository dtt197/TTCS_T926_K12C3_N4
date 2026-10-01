package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.Booking;
import java.time.LocalDate;

public record BookingListItemResponse(
        String bookingCode,
        String guestName,
        String roomTypeName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        long totalAmount,
        String status
) {
    public static BookingListItemResponse from(Booking booking) {
        return new BookingListItemResponse(
                booking.getBookingCode(),
                booking.getGuestName(),
                booking.getRoomTypeNameSnapshot(),
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                booking.getTotalAmount(),
                booking.getStatus().name()
        );
    }
}