package com.ttcs.homestay.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CheckInRequest(
        @NotNull(message = "Vui lòng chọn booking cần nhận phòng") Long bookingId,
        @NotBlank(message = "Vui lòng nhập họ tên khách chính")
        @Size(max = 120, message = "Họ tên khách chính không được vượt quá 120 ký tự")
        String primaryGuestName,
        @NotBlank(message = "Vui lòng nhập số CCCD khách chính")
        @Pattern(regexp = "[0-9]{12}", message = "Số CCCD phải gồm đúng 12 chữ số")
        String primaryGuestIdentityNumber,
        @NotNull(message = "Danh sách khách đi kèm không được để trống")
        List<@NotBlank(message = "Vui lòng nhập họ tên từng khách đi kèm")
                @Size(max = 120, message = "Họ tên khách đi kèm không được vượt quá 120 ký tự")
                String> accompanyingGuestNames) {
}