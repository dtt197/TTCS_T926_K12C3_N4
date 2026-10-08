package com.ttcs.homestay.dto.booking;

import java.math.BigDecimal;
import java.time.LocalDate;

public record BookingConfirmRequest(
        BigDecimal amount,
        String paymentMethod,
        LocalDate receivedDate,
        String paymentReference,
        String createdBy
) {
}
