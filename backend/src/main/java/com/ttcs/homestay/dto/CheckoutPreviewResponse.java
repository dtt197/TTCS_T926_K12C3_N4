package com.ttcs.homestay.dto;

import com.ttcs.homestay.dto.booking.BookingSurchargeResponse;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.time.LocalTime;
import java.time.OffsetDateTime;

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
        List<BookingSurchargeResponse> bookingSurcharges,
        BigDecimal bookingSurchargeTotal,
        BigDecimal depositTotal,
        LocalTime checkoutTimeSnapshot,
        OffsetDateTime serverCheckoutTime,
        Long operatingSettingsId,
        int lateCheckoutHours,
        Long lateCheckoutFeePerHour,
        BigDecimal lateCheckoutFee,
        BigDecimal settlementTotalBeforeDeposit,
        String lateCheckoutPricingBasis,
        boolean checkoutAllowed,
        String checkoutBlockedReason,
        String settlementType,
        BigDecimal amount,
        String previewToken) {
    public record NightlyLine(LocalDate date, String label, long roomPrice, long surchargeAmount, long total) {}
}
