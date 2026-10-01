package com.ttcs.homestay.dto.pricing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** S2-02 AC1: tên đợt, khoảng ngày áp dụng, loại phòng và giá một đêm (VND). */
public record PriceOverrideRequest(
        @NotBlank(message = "Vui lòng nhập tên đợt")
        @Size(max = 100, message = "Tên đợt tối đa 100 ký tự")
        String name,

        @NotNull(message = "Vui lòng chọn loại phòng")
        Long roomTypeId,

        @NotNull(message = "Vui lòng chọn ngày bắt đầu")
        LocalDate startDate,

        @NotNull(message = "Vui lòng chọn ngày kết thúc")
        LocalDate endDate,

        @NotNull(message = "Vui lòng nhập giá một đêm")
        @Min(value = 1, message = "Giá một đêm phải lớn hơn 0")
        @Max(value = 100_000_000, message = "Giá một đêm tối đa 100.000.000 đ")
        Long pricePerNight) {
}