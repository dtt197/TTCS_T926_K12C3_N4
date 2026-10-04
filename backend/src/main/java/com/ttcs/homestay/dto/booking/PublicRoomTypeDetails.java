package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.RoomType;

/** Thông tin chi tiết loại phòng công khai, không bao gồm dữ liệu quản trị. */
public record PublicRoomTypeDetails(
        Long id,
        String code,
        String name,
        Integer standardCapacity,
        Integer maxCapacity,
        Integer numberOfBeds,
        String description,
        Long weekdayPrice,
        Long weekendPrice) {

    public static PublicRoomTypeDetails from(RoomType roomType) {
        return new PublicRoomTypeDetails(
                roomType.getId(),
                roomType.getCode(),
                roomType.getName(),
                roomType.getStandardCapacity(),
                roomType.getMaxCapacity(),
                roomType.getNumberOfBeds(),
                roomType.getDescription(),
                roomType.getWeekdayPrice(),
                roomType.getWeekendPrice());
    }
}
