package com.ttcs.homestay.dto.booking;

import jakarta.validation.constraints.NotBlank;

/** S2-08 Lát 1: khách gửi mã booking và email đã dùng khi đặt để tra cứu lại booking. */
public record BookingLookupRequest(
        @NotBlank(message = "Vui lòng nhập mã booking và email") String bookingCode,
        @NotBlank(message = "Vui lòng nhập mã booking và email") String email) {
}