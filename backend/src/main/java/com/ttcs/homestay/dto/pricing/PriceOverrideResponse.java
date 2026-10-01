package com.ttcs.homestay.dto.pricing;

import com.ttcs.homestay.entity.PriceOverride;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/** S2-02: một đợt giá đè; nights = số đêm áp dụng (gồm cả ngày bắt đầu và ngày kết thúc). */
public record PriceOverrideResponse(
        Long id,
        String name,
        Long roomTypeId,
        String roomTypeCode,
        String roomTypeName,
        LocalDate startDate,
        LocalDate endDate,
        long nights,
        long pricePerNight,
        String createdByName,
        OffsetDateTime updatedAt) {

    public static PriceOverrideResponse from(PriceOverride priceOverride) {
        return new PriceOverrideResponse(
                priceOverride.getId(),
                priceOverride.getName(),
                priceOverride.getRoomType().getId(),
                priceOverride.getRoomType().getCode(),
                priceOverride.getRoomType().getName(),
                priceOverride.getStartDate(),
                priceOverride.getEndDate(),
                ChronoUnit.DAYS.between(priceOverride.getStartDate(), priceOverride.getEndDate()) + 1,
                priceOverride.getPricePerNight(),
                priceOverride.getCreatedByName(),
                priceOverride.getUpdatedAt());
    }
}