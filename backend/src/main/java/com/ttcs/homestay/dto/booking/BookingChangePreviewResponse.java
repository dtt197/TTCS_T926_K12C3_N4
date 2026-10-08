package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.dto.pricing.NightlyPrice;
import java.time.LocalDate;
import java.util.List;

public record BookingChangePreviewResponse(
        Long bookingId,
        Long roomTypeId,
        String roomTypeName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        int numberOfNights,
        long totalAmount,
        int availableRooms,
        boolean available,
        List<NightlyPrice> nightlyPrices
) {
}
