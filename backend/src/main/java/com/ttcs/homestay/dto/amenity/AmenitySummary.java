package com.ttcs.homestay.dto.amenity;

import com.ttcs.homestay.entity.Amenity;

/** S1-08 AC4: tiện nghi hiển thị kèm loại phòng (chỉ tiện nghi đang dùng). */
public record AmenitySummary(Long id, String code, String name, String icon) {

    public static AmenitySummary from(Amenity amenity) {
        return new AmenitySummary(amenity.getId(), amenity.getCode(), amenity.getName(), amenity.getIcon());
    }
}