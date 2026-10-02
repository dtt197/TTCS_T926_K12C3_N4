package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.dto.pricing.NightlyPrice;
import java.time.LocalDate;
import java.util.List;

/**
 * S2-06: giá tạm tính cho khách. totalAmount = nightsTotal + surchargeAmount.
 * Phụ thu = số người vượt sức chứa tiêu chuẩn × mức phụ thu × số đêm.
 */
public record GuestQuoteResponse(
        Long roomTypeId,
        String roomTypeName,
        LocalDate checkIn,
        LocalDate checkOut,
        int nights,
        List<NightlyPrice> nightlyPrices,
        long nightsTotal,
        int guestCount,
        int standardCapacity,
        int extraGuests,
        long extraPersonFee,
        long surchargeAmount,
        long totalAmount) {
}