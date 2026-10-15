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
        OffsetDateTime createdAt,
        long defaultUnitPrice,
        boolean priceOverridden,
        String priceOverrideReason,
        String priceOverriddenBy) {

    /** Giữ constructor của lát 1 cho code và test hiện có: coi như chưa sửa giá. */
    public BookingSurchargeResponse(Long id, SurchargeType type, int quantity, long unitPrice, int nights,
            long amount, String note, String createdBy, OffsetDateTime createdAt) {
        this(id, type, quantity, unitPrice, nights, amount, note, createdBy, createdAt,
                unitPrice, false, null, null);
    }

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
                surcharge.getCreatedAt(),
                surcharge.getDefaultUnitPrice(),
                surcharge.getPriceOverrideReason() != null,
                surcharge.getPriceOverrideReason(),
                surcharge.getPriceOverriddenBy());
    }
}