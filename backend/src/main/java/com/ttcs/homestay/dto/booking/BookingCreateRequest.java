package com.ttcs.homestay.dto.booking;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record BookingCreateRequest(
        @NotNull Long roomTypeId,
        @NotNull LocalDate checkInDate,
        @NotNull LocalDate checkOutDate) {
}