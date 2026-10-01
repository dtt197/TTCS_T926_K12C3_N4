package com.ttcs.homestay.dto.roomtype;

import com.ttcs.homestay.dto.amenity.AmenitySummary;
import com.ttcs.homestay.entity.Amenity;
import com.ttcs.homestay.entity.RoomType;
import java.util.Comparator;
import java.util.List;

/**
 * roomCount: số phòng đang gắn với loại phòng này.
 * amenities: chỉ gồm tiện nghi đang dùng.
 * weekdayPrice: S2-01 Lát 1, giá ngày thường theo VND/đêm.
 */
public record RoomTypeResponse(
        Long id,
        String code,
        String name,
        int standardCapacity,
        int maxCapacity,
        int numberOfBeds,
        String description,
        Long weekdayPrice,
        boolean active,
        long roomCount,
        List<AmenitySummary> amenities) {

    public static RoomTypeResponse from(RoomType roomType, long roomCount) {

        List<AmenitySummary> activeAmenities =
                roomType.getAmenities().stream()
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
                roomType.getWeekdayPrice(),
                !Boolean.FALSE.equals(roomType.getStatus()),
                roomCount,
                activeAmenities
        );
    }
}