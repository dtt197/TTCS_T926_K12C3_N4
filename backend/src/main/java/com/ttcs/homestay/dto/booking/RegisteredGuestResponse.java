package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.BookingGuest;

public record RegisteredGuestResponse(String fullName, boolean primary) {

    public static RegisteredGuestResponse from(BookingGuest guest) {
        return new RegisteredGuestResponse(guest.getFullName(), guest.getGuestOrder() == 0);
    }
}
