package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.BookingStatus;
import java.time.LocalDate;
import java.time.LocalTime;

/** S3-05: kết quả xem trước huỷ. appliedTierHours là null khi không thoả mốc nào (hoàn 0%). */
public record CancellationPreviewResponse(
        String bookingCode,
        String guestName,
        String roomTypeName,
        BookingStatus status,
        LocalDate checkInDate,
        LocalTime checkInTime,
        long hoursBeforeCheckIn,
        long depositAmount,
        Integer appliedTierHours,
        int refundPercent,
        long refundAmount
) {
}