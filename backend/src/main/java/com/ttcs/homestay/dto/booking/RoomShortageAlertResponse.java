package com.ttcs.homestay.dto.booking;

import java.time.LocalDate;

public record RoomShortageAlertResponse(
        LocalDate date,
        String roomTypeCode,
        String roomTypeName,
        long bookingCount,
        long availableRooms
) {}

