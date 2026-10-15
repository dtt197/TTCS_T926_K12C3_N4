package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.BookingSurcharge;
import com.ttcs.homestay.entity.SurchargeType;
import java.time.OffsetDateTime;

public record BookingSurchargeResponse(
        Long id,
        SurchargeType type,
        int quantity,
        long unitPrice,
        int nights,
        long amount,
        String note,
        String createdBy,
        OffsetDateTime createdAt) {

    public static BookingSurchargeResponse from(BookingSurcharge surcharge) {
        return new BookingSurchargeResponse(
                surcharge.getId(),
                surcharge.getSurchargeType(),
                surcharge.getQuantity(),
                surcharge.getUnitPrice(),
                surcharge.getNights(),
                surcharge.getAmount(),
                surcharge.getNote(),
                surcharge.getCreatedBy(),
                surcharge.getCreatedAt());
    }
}