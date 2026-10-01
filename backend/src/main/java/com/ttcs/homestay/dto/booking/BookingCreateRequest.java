package com.ttcs.homestay.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record BookingCreateRequest(
        @NotNull Long roomTypeId,

        @NotBlank
        @Size(max = 120)
        String guestName,

        @NotNull LocalDate checkInDate,

        @NotNull LocalDate checkOutDate
) {
}