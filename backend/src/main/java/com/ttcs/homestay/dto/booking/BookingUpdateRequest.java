package com.ttcs.homestay.dto.booking;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record BookingUpdateRequest(
        @NotNull(message = "Loại phòng không được để trống")
        Long roomTypeId,

        @NotNull(message = "Ngày nhận phòng không được để trống")
        LocalDate checkInDate,

        @NotNull(message = "Ngày trả phòng không được để trống")
        LocalDate checkOutDate
) {
}
