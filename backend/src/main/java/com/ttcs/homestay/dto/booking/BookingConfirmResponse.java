package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BookingConfirmResponse(
        Long id,
        String bookingCode,
        String status,
        BookingResponse booking,
        BookingDepositResponse deposit
) {

    public static BookingConfirmResponse from(Booking booking, BookingDepositResponse deposit) {
        return new BookingConfirmResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getStatus().name(),
                BookingResponse.from(booking),
                deposit != null ? deposit : new BookingDepositResponse(null, null, null, null, null, null, null, null)
        );
    }
}
