
package com.ttcs.homestay.dto.report;

/**
 * S4-07 Lát 2:
 * DTO thống kê doanh thu theo từng loại phòng
 * trong khoảng ngày được chọn.
 *
 * Tiền tính bằng đồng Việt Nam.
 * totalRevenue = roomRevenue + surchargeRevenue.
 * averageNightlyRate = null nếu không có đêm lưu trú.
 */
public record RevenueByRoomTypeResponse(
        Long roomTypeId,
        String roomTypeCode,
        String roomTypeName,
        long closedBookingCount,
        long totalNights,
        long roomRevenue,
        long surchargeRevenue,
        long totalRevenue,
        Long averageNightlyRate
) {
}
