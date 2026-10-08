package com.ttcs.homestay.dto.booking;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BookingDepositResponse(
        Long id,
        BigDecimal amount,
        String paymentMethod,
        LocalDate receivedDate,
        String paymentReference,
        String reservationCode,
        String createdBy,
        OffsetDateTime createdAt
) {
    public static BookingDepositResponse from(OffsetDateTime createdAt) {
        return new BookingDepositResponse(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                createdAt
        );
    }
}
