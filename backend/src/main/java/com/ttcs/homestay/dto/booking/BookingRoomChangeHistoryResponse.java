package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.BookingRoomChangeHistory;
import java.time.OffsetDateTime;

public record BookingRoomChangeHistoryResponse(
        Long id,
        Long bookingId,
        Long oldRoomId,
        String oldRoomNumber,
        Long newRoomId,
        String newRoomNumber,
        Long actorUserId,
        String actorName,
        OffsetDateTime changedAt,
        String reason) {
    public static BookingRoomChangeHistoryResponse from(BookingRoomChangeHistory history) {
        return new BookingRoomChangeHistoryResponse(
                history.getId(), history.getBooking().getId(),
                history.getOldRoom().getId(), history.getOldRoom().getRoomNumber(),
                history.getNewRoom().getId(), history.getNewRoom().getRoomNumber(),
                history.getActor().getId(), history.getActor().getFullName(),
                history.getChangedAt(), history.getReason());
    }
}
