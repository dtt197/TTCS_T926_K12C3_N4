package com.ttcs.homestay.dto.roomtype;

import com.ttcs.homestay.entity.RoomTypeImage;

public record RoomTypeImageResponse(
        Long id,
        Long roomTypeId,
        String imageUrl,
        String thumbnailUrl,
        int displayOrder,
        boolean isPrimary,
        String createdAt) {

    public static RoomTypeImageResponse from(RoomTypeImage image) {
        return new RoomTypeImageResponse(
                image.getId(),
                image.getRoomType().getId(),
                image.getImageUrl(),
                image.getThumbnailUrl(),
                image.getDisplayOrder(),
                Boolean.TRUE.equals(image.getIsPrimary()),
                image.getCreatedAt() != null ? image.getCreatedAt().toString() : null
        );
    }
}
