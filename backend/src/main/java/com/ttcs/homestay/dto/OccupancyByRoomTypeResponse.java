package com.ttcs.homestay.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** S4-06 AC3: công suất gộp và công suất theo loại phòng. */
public record OccupancyByRoomTypeResponse(
        LocalDate startDate,
        LocalDate endDate,
        Long selectedRoomTypeId,
        long totalSoldNights,
        long totalAvailableNights,
        BigDecimal totalAverageOccupancyPercent,
        List<RoomTypeOccupancy> roomTypes,
        List<DailyOccupancy> dailyOccupancies) {

    public record RoomTypeOccupancy(
            Long roomTypeId,
            String code,
            String name,
            boolean active,
            long soldNights,
            long availableNights,
            BigDecimal averageOccupancyPercent) {}

    public record DailyOccupancy(
            LocalDate date,
            long soldNights,
            long availableNights,
            BigDecimal occupancyPercent) {}
}
