package com.ttcs.homestay.dto;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

public record CheckoutPreviewResponse(
        Long bookingId,
        String bookingCode,
        Long roomId,
        String roomNumber,
        String guestName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        List<NightlyLine> nightlyPrices,
        boolean nightlyBreakdownReliable,
        String warning,
        BigDecimal roomTotal,
        BigDecimal surchargeAmount,
        boolean surchargeBreakdownComplete,
        Integer extraGuestCount,
        Long extraPersonFeeSnapshot,
        Long surchargeNights,
        BigDecimal roomTotalExcludingSurcharge,
        String surchargeBreakdownWarning,
        BigDecimal depositTotal,
        String settlementType,
        BigDecimal amount,
        String previewToken) {
    public record NightlyLine(LocalDate date, String label, long roomPrice, long surchargeAmount, long total) {}
}
