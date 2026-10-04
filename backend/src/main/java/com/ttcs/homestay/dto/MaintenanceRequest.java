package com.ttcs.homestay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record MaintenanceRequest(
        @NotBlank(message = "Lý do bảo trì không được để trống")
        String reason,
        @NotNull(message = "Ngày bắt đầu bảo trì là bắt buộc")
        LocalDate startDate,
        @NotNull(message = "Ngày kết thúc bảo trì là bắt buộc")
        LocalDate endDate
) {
}