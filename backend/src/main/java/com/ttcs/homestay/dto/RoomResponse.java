package com.ttcs.homestay.dto;

import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import java.time.LocalDate;

public record RoomResponse(
        Long id,
        String roomNumber,
        Integer floor,
        String roomType,
        RoomStatus status,
        boolean active,
        String maintenanceReason,
        LocalDate maintenanceStartDate,
        LocalDate maintenanceEndDate
) {
    public RoomResponse(Long id, String roomNumber, Integer floor, String roomType, RoomStatus status, boolean active) {
        this(id, roomNumber, floor, roomType, status, active, null, null, null);
    }

    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getRoomNumber(),
                room.getFloor(),
                room.getRoomType(),
                room.getStatus(),
                room.isActive(),
                room.getMaintenanceReason(),
                room.getMaintenanceStartDate(),
                room.getMaintenanceEndDate()
        );
    }
}