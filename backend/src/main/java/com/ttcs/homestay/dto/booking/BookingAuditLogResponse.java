package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.BookingAuditLog;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BookingAuditLogResponse(
        Long id,
        Long bookingId,
        String bookingCode,
        LocalDate oldCheckInDate,
        LocalDate newCheckInDate,
        LocalDate oldCheckOutDate,
        LocalDate newCheckOutDate,
        Long oldRoomTypeId,
        String oldRoomTypeName,
        Long newRoomTypeId,
        String newRoomTypeName,
        long oldTotalAmount,
        long newTotalAmount,
        Long actorUserId,
        String actorName,
        String actorEmail,
        OffsetDateTime createdAt
) {
    public static BookingAuditLogResponse from(BookingAuditLog log) {
        return new BookingAuditLogResponse(
                log.getId(),
                log.getBooking() != null ? log.getBooking().getId() : null,
                log.getBookingCode(),
                log.getOldCheckInDate(),
                log.getNewCheckInDate(),
                log.getOldCheckOutDate(),
                log.getNewCheckOutDate(),
                log.getOldRoomTypeId(),
                log.getOldRoomTypeName(),
                log.getNewRoomTypeId(),
                log.getNewRoomTypeName(),
                log.getOldTotalAmount(),
                log.getNewTotalAmount(),
                log.getActorUserId(),
                log.getActorName(),
                log.getActorEmail(),
                log.getCreatedAt()
        );
    }
}
