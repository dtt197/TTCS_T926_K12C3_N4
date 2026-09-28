package com.ttcs.homestay.dto.settings;

import com.ttcs.homestay.entity.OperatingSettings;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

/** S1-09: một phiên bản tham số; updatedByName và updatedAt là người sửa và thời điểm sửa (AC4). */
public record OperatingSettingsResponse(
        Long id,
        String homestayName,
        String address,
        String phone,
        String email,
        LocalTime checkInTime,
        LocalTime checkOutTime,
        long lateCheckoutFeePerHour,
        long extraPersonFee,
        List<Tier> cancellationTiers,
        String updatedByName,
        OffsetDateTime updatedAt) {

    public record Tier(int hoursBeforeCheckIn, int refundPercent) {
    }

    public static OperatingSettingsResponse from(OperatingSettings settings) {
        List<Tier> tiers = settings.getCancellationTiers().stream()
                .map(tier -> new Tier(tier.getHoursBeforeCheckIn(), tier.getRefundPercent()))
                .toList();
        return new OperatingSettingsResponse(
                settings.getId(),
                settings.getHomestayName(),
                settings.getAddress(),
                settings.getPhone(),
                settings.getEmail(),
                settings.getCheckInTime(),
                settings.getCheckOutTime(),
                settings.getLateCheckoutFeePerHour(),
                settings.getExtraPersonFee(),
                tiers,
                settings.getCreatedByName(),
                settings.getCreatedAt());
    }
}