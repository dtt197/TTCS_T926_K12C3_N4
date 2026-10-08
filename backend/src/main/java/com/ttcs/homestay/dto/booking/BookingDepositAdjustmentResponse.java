package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.BookingDepositAdjustment;
import com.ttcs.homestay.entity.DepositAdjustmentType;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record BookingDepositAdjustmentResponse(
        Long id,
        Long bookingId,
        DepositAdjustmentType adjustmentType,
        BigDecimal amount,
        String reason,
        String createdBy,
        OffsetDateTime createdAt,
        BigDecimal currentDepositTotal
) {
    public static BookingDepositAdjustmentResponse from(
            BookingDepositAdjustment adjustment, Long bookingId, BigDecimal currentDepositTotal) {
        return new BookingDepositAdjustmentResponse(
                adjustment.getId(),
                bookingId,
                adjustment.getAdjustmentType(),
                adjustment.getAmount(),
                adjustment.getReason(),
                adjustment.getCreatedBy(),
                adjustment.getCreatedAt(),
                currentDepositTotal);
    }
}
