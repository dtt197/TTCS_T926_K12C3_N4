package com.ttcs.homestay.dto.report;

import java.time.LocalDate;

/**
 * S4-07 Lát 1: thống kê doanh thu của các booking đã đóng trong khoảng ngày [startDate, endDate].
 * Tiền tính bằng đồng. totalRevenue = roomRevenue + surchargeRevenue.
 * averageNightlyRate là null khi không có đêm nào (chưa có booking đã đóng trong khoảng ngày).
 */
public record RevenueReportResponse(
        LocalDate startDate,
        LocalDate endDate,
        long closedBookingCount,
        long totalNights,
        long roomRevenue,
        long surchargeRevenue,
        long totalRevenue,
        Long averageNightlyRate) {
}