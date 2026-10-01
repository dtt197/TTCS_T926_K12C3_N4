package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.Booking;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/** S2-07 Lát 1: kết quả trả cho khách sau khi gửi yêu cầu đặt phòng thành công. */
public record GuestBookingResponse(
        String bookingCode,
        String status,
        String statusLabel,
        String roomTypeName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        long nights,
        long totalAmount,
        OffsetDateTime holdExpiresAt) {

    public static GuestBookingResponse from(Booking booking) {
        return new GuestBookingResponse(
                booking.getBookingCode(),
                booking.getStatus().name(),
                "Chờ xác nhận",
                booking.getRoomTypeNameSnapshot(),
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate()),
                booking.getTotalAmount(),
                booking.getHoldExpiresAt());
    }
}