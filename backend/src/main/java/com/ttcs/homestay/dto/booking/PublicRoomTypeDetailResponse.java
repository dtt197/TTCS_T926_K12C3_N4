package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.dto.amenity.AmenitySummary;
import com.ttcs.homestay.entity.Amenity;
import com.ttcs.homestay.entity.RoomType;
import java.util.Comparator;
import java.util.List;

/**
 * S2-04: Chi tiết loại phòng công khai cho khách xem trước khi đặt phòng.
 * Bao gồm tên, bộ ảnh, mô tả, tiện nghi, sức chứa, số phòng còn trống và giá.
 */
public record PublicRoomTypeDetailResponse(
        Long id,
        String code,
        String name,
        int standardCapacity,
        int maxCapacity,
        int numberOfBeds,
        String description,
        Long weekdayPrice,
        Long weekendPrice,
        int availableRooms,
        List<AmenitySummary> amenities,
        List<String> images
) {

    public static PublicRoomTypeDetailResponse from(RoomType roomType, int availableRooms) {
        List<AmenitySummary> activeAmenities = roomType.getAmenities().stream()
                .filter(Amenity::isActive)
                .sorted(Comparator.comparing(Amenity::getName))
                .map(AmenitySummary::from)
                .toList();

        List<String> images = defaultImagesFor(roomType.getCode());

        return new PublicRoomTypeDetailResponse(
                roomType.getId(),
                roomType.getCode(),
                roomType.getName(),
                roomType.getStandardCapacity(),
                roomType.getMaxCapacity(),
                roomType.getNumberOfBeds(),
                roomType.getDescription(),
                roomType.getWeekdayPrice(),
                roomType.getWeekendPrice(),
                availableRooms,
                activeAmenities,
                images
        );
    }

    public static List<String> defaultImagesFor(String code) {
        String upper = code == null ? "" : code.toUpperCase();
        if (upper.contains("DON")) {
            return List.of(
                    "https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1566665797739-1674de7a421a?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1616486338812-3dadae4b4ace?auto=format&fit=crop&w=1200&q=80"
            );
        } else if (upper.contains("GIA_DINH") || upper.contains("FAMILY")) {
            return List.of(
                    "https://images.unsplash.com/photo-1578683010236-d716f9a3f461?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1595526114035-0d45ed16cfbf?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1507038772120-7ffe76778f01?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1618773928121-c32242e63f39?auto=format&fit=crop&w=1200&q=80"
            );
        } else {
            // Default / DOI / Double
            return List.of(
                    "https://images.unsplash.com/photo-1618773928121-c32242e63f39?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1591088398332-8a7791972843?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1598928506311-c55ded91a20c?auto=format&fit=crop&w=1200&q=80",
                    "https://images.unsplash.com/photo-1582719508461-905c673771fd?auto=format&fit=crop&w=1200&q=80"
            );
        }
    }
}
