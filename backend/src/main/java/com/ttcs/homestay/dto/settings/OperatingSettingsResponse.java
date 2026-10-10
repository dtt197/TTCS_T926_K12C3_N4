package com.ttcs.homestay.dto.settings;

import com.ttcs.homestay.entity.OperatingSettings;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * S1-09: một phiên bản tham số;
 * updatedByName và updatedAt là người sửa và thời điểm sửa.
 *
 * S2-01 Lát 3: trả danh sách ngày cuối tuần.
 */
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
        List<String> weekendDays,
        String updatedByName,
        OffsetDateTime updatedAt) {

    public record Tier(
            int hoursBeforeCheckIn,
            int refundPercent) {
    }

    public static OperatingSettingsResponse from(
            OperatingSettings settings) {

        List<Tier> tiers =
                settings.getCancellationTiers()
                        .stream()
                        .map(tier -> new Tier(
                                tier.getHoursBeforeCheckIn(),
                                tier.getRefundPercent()
                        ))
                        .toList();

        List<String> weekendDays =
                settings.getWeekendDays() == null
                        || settings.getWeekendDays().isBlank()
                        ? List.of("FRIDAY", "SATURDAY")
                        : Arrays.stream(
                                        settings.getWeekendDays().split(",")
                                )
                                .map(value -> value.trim())
                                .filter(value -> !value.isBlank())
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
                weekendDays,
                settings.getCreatedByName(),
                settings.getCreatedAt()
        );
    }
}