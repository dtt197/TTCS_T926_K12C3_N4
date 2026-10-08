package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.DepositAdjustmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record BookingDepositAdjustmentRequest(
        @NotNull(message = "Loại điều chỉnh là bắt buộc")
        @JsonProperty("type")
        @JsonAlias("adjustmentType")
        DepositAdjustmentType adjustmentType,

        @NotNull(message = "Số tiền điều chỉnh là bắt buộc")
        @Positive(message = "Số tiền điều chỉnh phải lớn hơn 0")
        BigDecimal amount,

        @NotBlank(message = "Lý do điều chỉnh là bắt buộc")
        @Size(max = 500, message = "Lý do điều chỉnh không được vượt quá 500 ký tự")
        String reason
) {
}
