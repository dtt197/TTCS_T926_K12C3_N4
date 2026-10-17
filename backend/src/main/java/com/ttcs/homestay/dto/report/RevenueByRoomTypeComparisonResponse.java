package com.ttcs.homestay.dto.report;

import java.math.BigDecimal;

public record RevenueByRoomTypeComparisonResponse(
        Long roomTypeId,
        String roomTypeCode,
        String roomTypeName,
        long currentRevenue,
        long previousRevenue,
        long differenceAmount,
        BigDecimal changePercent,
        String changeDirection,
        String message
) {
}