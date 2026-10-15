package com.ttcs.homestay.dto.booking;

import java.util.List;

/**
 * S4-02 Lát 1: bảng phụ thu của booking, tổng phụ thu
 * và đơn giá mặc định để biểu mẫu hiện ô đơn giá chỉ đọc.
 * S4-02 Lát 3: số khách hiện tại, sức chứa tối đa của loại phòng và số chỗ còn lại.
 */
public record BookingSurchargeListResponse(
        Long bookingId,
        boolean canAddSurcharge,
        int maxNights,
        long defaultExtraPersonPrice,
        long defaultExtraBedPrice,
        List<BookingSurchargeResponse> items,
        long totalAmount,
        Integer currentGuestCount,
        Integer maxCapacity,
        Integer remainingCapacity) {

    /** Giữ constructor của lát 1 cho code và test hiện có. */
    public BookingSurchargeListResponse(Long bookingId, boolean canAddSurcharge, int maxNights,
            long defaultExtraPersonPrice, long defaultExtraBedPrice,
            List<BookingSurchargeResponse> items, long totalAmount) {
        this(bookingId, canAddSurcharge, maxNights, defaultExtraPersonPrice, defaultExtraBedPrice,
                items, totalAmount, null, null, null);
    }
}