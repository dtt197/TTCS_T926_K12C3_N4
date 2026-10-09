package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.BookingStatus;

/** S3-05: kết quả sau khi huỷ, trùng với số đã lưu trong booking. */
public record BookingCancellationResponse(
        String bookingCode,
        BookingStatus status,
        String cancelReason,
        String cancelNote,
        long depositAmount,
        int refundPercent,
        long refundAmount
) {
}