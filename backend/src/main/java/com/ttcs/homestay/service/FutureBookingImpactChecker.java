package com.ttcs.homestay.service;

import java.time.LocalDate;

public interface FutureBookingImpactChecker {

    FutureBookingImpact check(
            Long roomId,
            String currentRoomNumber,
            String newRoomNumber,
            String currentRoomType,
            String newRoomType,
            Integer currentFloor,
            Integer newFloor,
            LocalDate today);

    record FutureBookingImpact(
            boolean checkAvailable,
            int affectedBookings,
            String message) {

        public boolean warningRequired() {
            return affectedBookings > 0;
        }
    }
}
