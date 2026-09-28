package com.ttcs.homestay.dto.roomtype;

import com.ttcs.homestay.entity.RoomType;

/** roomCount: số phòng đang gắn với loại phòng này, frontend dùng để khoá nút Xoá. */
public record RoomTypeResponse(
        Long id,
        String code,
        String name,
        int standardCapacity,
        int maxCapacity,
        int numberOfBeds,
        String description,
        boolean active,
        long roomCount) {

    public static RoomTypeResponse from(RoomType roomType, long roomCount) {
        return new RoomTypeResponse(
                roomType.getId(),
                roomType.getCode(),
                roomType.getName(),
                roomType.getStandardCapacity(),
                roomType.getMaxCapacity(),
                roomType.getNumberOfBeds(),
                roomType.getDescription(),
                !Boolean.FALSE.equals(roomType.getStatus()),
                roomCount);
    }
}