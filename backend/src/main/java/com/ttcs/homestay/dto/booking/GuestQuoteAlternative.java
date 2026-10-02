package com.ttcs.homestay.dto.booking;

/** Loại phòng thay thế phù hợp với số khách và khoảng ngày đang chọn. */
public record GuestQuoteAlternative(
        Long roomTypeId,
        String roomTypeName,
        int maxCapacity,
        long totalAmount) {
}
