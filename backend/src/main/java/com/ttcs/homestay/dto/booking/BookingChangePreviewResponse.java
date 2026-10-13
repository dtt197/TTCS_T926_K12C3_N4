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
        List<NightlyPrice> nightlyPrices,
        /** S3-04 AC2: tổng tiền cũ và chênh lệch (tổng mới − tổng cũ). */
        long oldTotalAmount,
        long totalDifference,
        /** S3-04 AC3: tiền cọc đã ghi nhận được giữ nguyên và số còn phải thu (tổng mới − tiền cọc). */
        long depositAmount,
        long remainingAmount
) {
    public BookingChangePreviewResponse(
            Long bookingId,
            Long roomTypeId,
            String roomTypeName,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            int numberOfNights,
            long totalAmount,
            int availableRooms,
            boolean available,
            List<NightlyPrice> nightlyPrices) {
        this(bookingId, roomTypeId, roomTypeName, checkInDate, checkOutDate, numberOfNights, totalAmount,
                availableRooms, available, nightlyPrices, totalAmount, 0L, 0L, totalAmount);
    }
}