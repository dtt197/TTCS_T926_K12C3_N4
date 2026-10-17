package com.ttcs.homestay.dto.report;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenueComparisonResponse(
        LocalDate currentStartDate,
        LocalDate currentEndDate,
        LocalDate previousStartDate,
        LocalDate previousEndDate,
        long currentRevenue,
        long previousRevenue,
        long differenceAmount,
        BigDecimal changePercent,
        String changeDirection,
        String message
) {
}