package com.ttcs.homestay.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** S4-06: Dữ liệu nguồn dùng chung cho màn hình và tệp CSV. */
public record OccupancyReportResponse(
        LocalDate startDate,
        LocalDate endDate,
        Long selectedRoomTypeId,
        String selectedRoomTypeName,
        long totalSoldNights,
        long totalAvailableNights,
        BigDecimal totalAverageOccupancyPercent,
        List<RoomTypeOccupancy> roomTypes,
        List<DailyOccupancy> dailyOccupancies) {

    public record RoomTypeOccupancy(Long roomTypeId, String code, String name,
                                    boolean active, long soldNights, long availableNights,
                                    BigDecimal averageOccupancyPercent) { }

    public record DailyOccupancy(LocalDate date, long soldNights, long availableNights,
                                 BigDecimal occupancyPercent) { }
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
