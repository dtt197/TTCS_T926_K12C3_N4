package com.ttcs.homestay.dto;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.CheckIn;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.RoomStatus;
import java.time.OffsetDateTime;

public record CheckInResponse(
        Long id,
        Long bookingId,
        String bookingCode,
        BookingStatus bookingStatus,
        Long roomId,
        String roomNumber,
        RoomStatus roomStatus,
        String guestName,
        OffsetDateTime checkedInAt
) {
    public static CheckInResponse from(CheckIn checkIn, Booking booking) {
        return new CheckInResponse(
                checkIn.getId(),
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus(),
                checkIn.getRoom().getId(),
                checkIn.getRoom().getRoomNumber(),
                checkIn.getRoom().getStatus(),
                checkIn.getGuestName(),
                checkIn.getCheckedInAt()
        );
    }
}