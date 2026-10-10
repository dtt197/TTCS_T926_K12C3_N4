package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.Booking;
import java.time.LocalDate;

public record CheckInOptionResponse(
        Long bookingId,
        String bookingCode,
        String guestName,
        Long roomId,
        String roomNumber,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        Integer requestedGuestCount,
        Integer maxGuests
) {
    public static CheckInOptionResponse from(Booking booking) {
        return new CheckInOptionResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getGuestName(),
                booking.getRoom().getId(),
                booking.getRoom().getRoomNumber(),
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                booking.getGuestCount(),
                booking.getRoomType().getMaxCapacity());
    }
}
