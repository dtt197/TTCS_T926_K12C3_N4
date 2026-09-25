package com.ttcs.homestay.dto;

import com.ttcs.homestay.entity.CheckIn;
import java.time.OffsetDateTime;

public record CheckInResponse(
        Long id,
        Long roomId,
        String roomNumber,
        String guestName,
        OffsetDateTime checkedInAt
) {
    public static CheckInResponse from(CheckIn checkIn) {
        return new CheckInResponse(
                checkIn.getId(),
                checkIn.getRoom().getId(),
                checkIn.getRoom().getRoomNumber(),
                checkIn.getGuestName(),
                checkIn.getCheckedInAt()
        );
    }
}