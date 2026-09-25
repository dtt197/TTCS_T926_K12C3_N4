package com.ttcs.homestay.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckInRequest(@NotBlank(message = "Tên khách không được để trống") String guestName) {
}