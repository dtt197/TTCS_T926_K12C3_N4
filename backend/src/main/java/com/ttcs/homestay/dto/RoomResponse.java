package com.ttcs.homestay.dto;

import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;

public record RoomResponse(
        Long id,
        String roomNumber,
        Integer floor,
        String roomType,
        RoomStatus status,
        boolean active
) {
    public static RoomResponse from(Room room) {
        return new RoomResponse(
                room.getId(),
                room.getRoomNumber(),
                room.getFloor(),
                room.getRoomType(),
                room.getStatus(),
                room.isActive()
        );
    }
}