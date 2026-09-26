package com.ttcs.homestay.dto;

import com.ttcs.homestay.entity.RoomStatusHistory;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record RoomStatusHistoryResponse(
        Long id,
        Long roomId,
        String roomNumber,
        String previousStatus,
        String newStatus,
        String changedBy,
        OffsetDateTime changedAt,
        String maintenanceReason,
        LocalDate maintenanceStartDate,
        LocalDate maintenanceEndDate
) {
    public static RoomStatusHistoryResponse from(
            RoomStatusHistory history
    ) {
        return new RoomStatusHistoryResponse(
                history.getId(),
                history.getRoom().getId(),
                history.getRoom().getRoomNumber(),
                history.getPreviousStatus().name(),
                history.getNewStatus().name(),
                history.getChangedBy(),
                history.getChangedAt(),
                history.getMaintenanceReason(),
                history.getMaintenanceStartDate(),
                history.getMaintenanceEndDate()
        );
    }
}