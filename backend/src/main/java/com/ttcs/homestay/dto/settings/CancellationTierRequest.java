package com.ttcs.homestay.dto.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** S1-09 AC2: huỷ trước ít nhất N giờ so với giờ nhận phòng thì hoàn X% tiền cọc. */
public record CancellationTierRequest(
        @NotNull(message = "Vui lòng nhập số giờ của mốc huỷ")
        @Min(value = 0, message = "Số giờ của mốc huỷ không được âm")
        @Max(value = 720, message = "Số giờ của mốc huỷ tối đa 720 (30 ngày)")
        Integer hoursBeforeCheckIn,

        @NotNull(message = "Vui lòng nhập tỷ lệ hoàn cọc")
        @Min(value = 0, message = "Tỷ lệ hoàn cọc từ 0 đến 100%")
        @Max(value = 100, message = "Tỷ lệ hoàn cọc từ 0 đến 100%")
        Integer refundPercent) {
}