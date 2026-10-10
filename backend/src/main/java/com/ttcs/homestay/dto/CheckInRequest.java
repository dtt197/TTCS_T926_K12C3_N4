package com.ttcs.homestay.dto;

import jakarta.validation.constraints.NotNull;

public record CheckInRequest(
        @NotNull(message = "Vui lòng chọn booking cần nhận phòng") Long bookingId) {
}