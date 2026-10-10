package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.service.BookingHoldPolicy;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * S3-08: Một dòng booking hiển thị khi lễ tân bấm vào cảnh báo thiếu phòng.
 * Chứa: mã booking, tên khách, số điện thoại, ngày nhận/trả phòng, trạng thái, kênh đặt, thời điểm tạo.
 */
public record ShortageBookingResponse(
        Long id,
        String bookingCode,
        String guestName,
        String guestPhone,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        BookingStatus status,
        boolean holdExpired,
        String source,
        OffsetDateTime createdAt
) {
    public static ShortageBookingResponse from(Booking booking) {
        boolean expired = booking.getStatus() == BookingStatus.CHO_XAC_NHAN && (
                (booking.getHoldExpiresAt() != null
                        && booking.getHoldExpiresAt().toInstant().isBefore(Instant.now()))
                || BookingHoldPolicy.isExpired(
                        booking.getStatus(),
                        booking.getCreatedAt() != null ? booking.getCreatedAt().toInstant() : null,
                        Instant.now()
                )
        );

        return new ShortageBookingResponse(
                booking.getId(),
                booking.getBookingCode(),
                booking.getGuestName(),
                booking.getGuestPhone(),
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                booking.getStatus(),
                expired,
                booking.getSource() != null ? booking.getSource().name() : null,
                booking.getCreatedAt()
        );
    }
}
