package com.ttcs.homestay.service;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * SCRUM-12: backend hiện tại chưa có bảng/module Booking.
 * Không tự suy đoán booking tương lai; trả trạng thái chưa thể kiểm tra.
 */
@Component
public class NoBookingModuleImpactChecker implements FutureBookingImpactChecker {

    @Override
    public FutureBookingImpact check(
            Long roomId,
            String currentRoomNumber,
            String newRoomNumber,
            String currentRoomType,
            String newRoomType,
            Integer currentFloor,
            Integer newFloor,
            LocalDate today) {
        return new FutureBookingImpact(
                false,
                0,
                "Chưa có module Booking trong backend hiện tại nên chưa thể kiểm tra booking tương lai.");
    }
}
