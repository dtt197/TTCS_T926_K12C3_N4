package com.ttcs.homestay.dto.roomtype;

import com.ttcs.homestay.dto.amenity.AmenitySummary;
import com.ttcs.homestay.entity.Amenity;
import com.ttcs.homestay.entity.RoomType;
import java.util.Comparator;
import java.util.List;

/**
 * roomCount: số phòng đang gắn với loại phòng này, frontend dùng để khoá nút Xoá.
 * amenities: S1-08 AC4, chỉ gồm tiện nghi đang dùng (tiện nghi đã ngừng dùng bị ẩn).
 */
public record RoomTypeResponse(
        Long id,
        String code,
        String name,
        int standardCapacity,
        int maxCapacity,
        int numberOfBeds,
        String description,
        boolean active,
        long roomCount,
        List<AmenitySummary> amenities) {

    public static RoomTypeResponse from(RoomType roomType, long roomCount) {
        List<AmenitySummary> activeAmenities = roomType.getAmenities().stream()
                .filter(Amenity::isActive)
                .sorted(Comparator.comparing(Amenity::getName))
                .map(AmenitySummary::from)
                .toList();
        return new RoomTypeResponse(
                roomType.getId(),
                roomType.getCode(),
                roomType.getName(),
                roomType.getStandardCapacity(),
                roomType.getMaxCapacity(),
                roomType.getNumberOfBeds(),
                roomType.getDescription(),
                !Boolean.FALSE.equals(roomType.getStatus()),
                roomCount,
                activeAmenities);
    }
}