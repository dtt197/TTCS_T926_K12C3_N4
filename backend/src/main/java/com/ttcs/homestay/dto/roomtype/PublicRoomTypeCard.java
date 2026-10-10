package com.ttcs.homestay.dto.roomtype;

import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.entity.RoomTypeImage;

/** S2-03: thẻ loại phòng trên trang công khai. fromPrice là giá thấp nhất trong các giá đã khai báo, null nếu chưa có giá. */
public record PublicRoomTypeCard(
        Long id,
        String name,
        int standardCapacity,
        int maxCapacity,
        Long fromPrice,
        String imageUrl,
        String imageAlt,
        long activeRoomCount) {

    public static PublicRoomTypeCard from(RoomType roomType, long activeRoomCount) {
        String alt = roomType.getImageAlt();
        if (alt == null || alt.isBlank()) {
            alt = roomType.getName() == null || roomType.getName().isBlank()
                    ? "Ảnh loại phòng"
                    : "Ảnh " + roomType.getName();
        }
        RoomTypeImage coverImage = roomType.getImages().stream()
                .filter(image -> Boolean.TRUE.equals(image.getIsPrimary()))
                .findFirst()
                .orElseGet(() -> roomType.getImages().stream().findFirst().orElse(null));
        String imageUrl = coverImage == null
                ? nonBlankOrNull(roomType.getImageUrl())
                : nonBlankOrNull(coverImage.getThumbnailUrl());
        if (coverImage != null && imageUrl == null) {
            imageUrl = nonBlankOrNull(coverImage.getImageUrl());
        }
        if (imageUrl == null) {
            imageUrl = nonBlankOrNull(roomType.getImageUrl());
        }
        return new PublicRoomTypeCard(
                roomType.getId(),
                roomType.getName(),
                roomType.getStandardCapacity(),
                roomType.getMaxCapacity(),
                lowestPrice(roomType.getWeekdayPrice(), roomType.getWeekendPrice()),
                imageUrl,
                alt,
                activeRoomCount);
    }

    private static String nonBlankOrNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    static Long lowestPrice(Long weekdayPrice, Long weekendPrice) {
        boolean hasWeekday = weekdayPrice != null && weekdayPrice > 0;
        boolean hasWeekend = weekendPrice != null && weekendPrice > 0;
        if (weekdayPrice != null && weekendPrice != null && weekdayPrice > 0 && weekendPrice > 0) {
            long weekday = weekdayPrice;
            long weekend = weekendPrice;
            return Math.min(weekday, weekend);
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