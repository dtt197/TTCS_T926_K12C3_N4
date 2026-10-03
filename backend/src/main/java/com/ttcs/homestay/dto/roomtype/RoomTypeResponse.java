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
 * avatarUrl: S2-09 ảnh đại diện (bản thu nhỏ) của loại phòng.
 * images: S2-09 danh sách ảnh theo thứ tự hiển thị.
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
        Long weekendPrice,
        boolean active,
        long roomCount,
        List<AmenitySummary> amenities,
        String avatarUrl,
        List<RoomTypeImageResponse> images) {

    public RoomTypeResponse(
            Long id,
            String code,
            String name,
            int standardCapacity,
            int maxCapacity,
            int numberOfBeds,
            String description,
            Long weekdayPrice,
            Long weekendPrice,
            boolean active,
            long roomCount,
            List<AmenitySummary> amenities) {
        this(id, code, name, standardCapacity, maxCapacity, numberOfBeds, description, weekdayPrice, weekendPrice, active, roomCount, amenities, null, List.of());
    }

    public static RoomTypeResponse from(RoomType roomType, long roomCount) {

        List<AmenitySummary> activeAmenities =
                roomType.getAmenities() != null ? roomType.getAmenities().stream()
                        .filter(Amenity::isActive)
                        .sorted(Comparator.comparing(Amenity::getName))
                        .map(AmenitySummary::from)
                        .toList() : List.of();

        List<RoomTypeImageResponse> imageResponses =
                roomType.getImages() != null ? roomType.getImages().stream()
                        .map(RoomTypeImageResponse::from)
                        .toList() : List.of();

        String avatar = null;
        if (roomType.getImages() != null && !roomType.getImages().isEmpty()) {
            avatar = roomType.getImages().stream()
                    .filter(img -> Boolean.TRUE.equals(img.getIsPrimary()))
                    .map(img -> img.getThumbnailUrl() != null ? img.getThumbnailUrl() : img.getImageUrl())
                    .findFirst()
                    .orElse(roomType.getImages().get(0).getThumbnailUrl());
        }

        return new RoomTypeResponse(
                roomType.getId(),
                roomType.getCode(),
                roomType.getName(),
                roomType.getStandardCapacity(),
                roomType.getMaxCapacity(),
                roomType.getNumberOfBeds(),
                roomType.getDescription(),
                roomType.getWeekdayPrice(),
                roomType.getWeekendPrice(),
                !Boolean.FALSE.equals(roomType.getStatus()),
                roomCount,
                activeAmenities,
                avatar,
                imageResponses
        );
    }
}