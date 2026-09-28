package com.ttcs.homestay.dto;

public record RoomUpdateResponse(
        RoomResponse room,
        String note,
        boolean bookingCheckAvailable,
        boolean warningRequired,
        int affectedFutureBookings,
        String warningMessage
) {
}
