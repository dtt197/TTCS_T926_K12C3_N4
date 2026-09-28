package com.ttcs.homestay.dto.amenity;

import com.ttcs.homestay.entity.Amenity;

/** roomTypeCount: số loại phòng đang gắn tiện nghi này (AC3: > 0 thì không xoá được). */
public record AmenityResponse(
        Long id,
        String code,
        String name,
        String icon,
        boolean active,
        long roomTypeCount) {

    public static AmenityResponse from(Amenity amenity, long roomTypeCount) {
        return new AmenityResponse(
                amenity.getId(),
                amenity.getCode(),
                amenity.getName(),
                amenity.getIcon(),
                amenity.isActive(),
                roomTypeCount);
    }
}