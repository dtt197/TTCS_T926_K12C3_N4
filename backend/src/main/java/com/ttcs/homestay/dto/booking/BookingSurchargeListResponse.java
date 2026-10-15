package com.ttcs.homestay.dto.booking;

import java.util.List;

/**
 * S4-02 Lát 1: bảng phụ thu của booking, tổng phụ thu
 * và đơn giá mặc định để biểu mẫu hiện ô đơn giá chỉ đọc.
 */
public record BookingSurchargeListResponse(
        Long bookingId,
        boolean canAddSurcharge,
        int maxNights,
        long defaultExtraPersonPrice,
        long defaultExtraBedPrice,
        List<BookingSurchargeResponse> items,
        long totalAmount) {
}