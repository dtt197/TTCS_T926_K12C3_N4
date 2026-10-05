package com.ttcs.homestay.dto.booking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.entity.RoomTypeImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicRoomTypeDetailResponseTest {

    @Test
    void from_usesUploadedRoomImagesInsteadOfLegacyCoverImage() {
        RoomType roomType = roomType();
        roomType.setImageUrl("/room-images/room-1.jpg");
        RoomTypeImage firstImage = new RoomTypeImage();
        firstImage.setImageUrl("/uploads/room-types/1/room-front.jpg");
        RoomTypeImage secondImage = new RoomTypeImage();
        secondImage.setImageUrl("/uploads/room-types/1/room-bed.jpg");
        roomType.setImages(List.of(firstImage, secondImage));

        PublicRoomTypeDetailResponse response = PublicRoomTypeDetailResponse.from(roomType, 1, settings());

        assertEquals(
                List.of("/uploads/room-types/1/room-front.jpg", "/uploads/room-types/1/room-bed.jpg"),
                response.images());
    }

    @Test
    void from_usesLegacyRoomTypeImageWhenNoUploadedImagesExist() {
        RoomType roomType = roomType();
        roomType.setImageUrl("/room-images/room-1.jpg");

        PublicRoomTypeDetailResponse response = PublicRoomTypeDetailResponse.from(roomType, 1, settings());

        assertEquals(List.of("/room-images/room-1.jpg"), response.images());
    }

    @Test
    void from_returnsNoImagesRatherThanMockImagesWhenRoomTypeHasNoImage() {
        PublicRoomTypeDetailResponse response = PublicRoomTypeDetailResponse.from(roomType(), 1, settings());

        assertTrue(response.images().isEmpty());
    }

    @Test
    void from_usesRoomTypeExtraPersonFeeWhenConfigured() {
        RoomType roomType = roomType();
        roomType.setExtraPersonFee(250_000L);
        OperatingSettings settings = settings();
        settings.setExtraPersonFee(200_000L);

        PublicRoomTypeDetailResponse response = PublicRoomTypeDetailResponse.from(roomType, 1, settings);

        assertEquals(250_000L, response.extraPersonFee());
    }

    @Test
    void from_fallsBackToOperatingSettingsWhenRoomTypeFeeIsNotConfigured() {
        OperatingSettings settings = settings();
        settings.setExtraPersonFee(200_000L);

        PublicRoomTypeDetailResponse response = PublicRoomTypeDetailResponse.from(roomType(), 1, settings);

        assertEquals(200_000L, response.extraPersonFee());
    }

    private static OperatingSettings settings() {
        OperatingSettings settings = new OperatingSettings();
        settings.setExtraPersonFee(200_000L);
        return settings;
    }

    private static RoomType roomType() {
        RoomType roomType = new RoomType();
        roomType.setCode("TEST");
        roomType.setName("Phòng kiểm thử");
        roomType.setStandardCapacity(2);
        roomType.setMaxCapacity(2);
        roomType.setNumberOfBeds(1);
        return roomType;
    }
}
