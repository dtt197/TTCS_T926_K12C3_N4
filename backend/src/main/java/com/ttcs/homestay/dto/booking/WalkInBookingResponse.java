package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingSource;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * S3-06 Lát 4: thông tin booking tại quầy trả về ngay sau khi tạo, để lễ tân đọc hoặc in cho khách.
 */
public record WalkInBookingResponse(
        String bookingCode,
        String status,
        String statusLabel,
        String source,
        String sourceLabel,
        String guestName,
        String roomTypeName,
        String roomNumber,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        long nights,
        int guestCount,
        OffsetDateTime createdAt) {

    public static WalkInBookingResponse from(Booking booking) {
        return new WalkInBookingResponse(
                booking.getBookingCode(),
                booking.getStatus().name(),
                BookingLookupResponse.statusLabel(booking.getStatus()),
                booking.getSource().name(),
                sourceLabel(booking.getSource()),
                booking.getGuestName(),
                booking.getRoomTypeNameSnapshot(),
                booking.getRoom() == null ? null : booking.getRoom().getRoomNumber(),
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate()),
                booking.getGuestCount() == null ? 0 : booking.getGuestCount(),
                booking.getCreatedAt());
    }

    static String sourceLabel(BookingSource source) {
        return switch (source) {
            case TRUC_TUYEN -> "Trực tuyến";
            case TAI_QUAY -> "Tại quầy";
        };
    }
}