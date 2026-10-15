package com.ttcs.homestay.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** S4-06: Báo cáo gộp tất cả loại phòng, theo từng đêm nhận phòng. */
public record OccupancyReportResponse(
        LocalDate startDate,
        LocalDate endDate,
        long totalSoldNights,
        long totalAvailableNights,
        BigDecimal averageOccupancyPercent,
        List<DailyOccupancy> dailyOccupancies) {

    public record DailyOccupancy(
            LocalDate date,
            long soldNights,
            long availableNights,
            BigDecimal occupancyPercent) {
    }
}
