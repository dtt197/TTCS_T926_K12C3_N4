package com.ttcs.homestay.dto.pricing;

import java.time.LocalDate;
import java.util.List;

/** S2-02: giá từng đêm và tổng tiền cho một khoảng ngày nhận - trả phòng. */
public record PriceQuoteResponse(
        Long roomTypeId,
        String roomTypeName,
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        List<NightlyPrice> nightlyPrices,
        long totalAmount) {
}