package com.ttcs.homestay.dto.roomtype;

import com.ttcs.homestay.entity.RoomType;

/** S2-03: thẻ loại phòng trên trang công khai. fromPrice là giá thấp nhất trong các giá đã khai báo, null nếu chưa có giá. */
public record PublicRoomTypeCard(
        Long id,
        String name,
        int standardCapacity,
        int maxCapacity,
        Long fromPrice,
        String imageUrl,
        String imageAlt) {

    public static PublicRoomTypeCard from(RoomType roomType) {
        String alt = roomType.getImageAlt();
        if (alt == null || alt.isBlank()) {
            alt = roomType.getName();
        }
        return new PublicRoomTypeCard(
                roomType.getId(),
                roomType.getName(),
                roomType.getStandardCapacity(),
                roomType.getMaxCapacity(),
                lowestPrice(roomType.getWeekdayPrice(), roomType.getWeekendPrice()),
                roomType.getImageUrl(),
                alt);
    }

    static Long lowestPrice(Long weekdayPrice, Long weekendPrice) {
        boolean hasWeekday = weekdayPrice != null && weekdayPrice > 0;
        boolean hasWeekend = weekendPrice != null && weekendPrice > 0;
        if (hasWeekday && hasWeekend) {
            return Math.min(weekdayPrice, weekendPrice);
        }
        if (hasWeekday) {
            return weekdayPrice;
        }
        if (hasWeekend) {
            return weekendPrice;
        }
        return null;
    }
}