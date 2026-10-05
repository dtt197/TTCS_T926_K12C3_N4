package com.ttcs.homestay.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record MaintenanceRequest(
        @NotBlank(message = "Lý do bảo trì không được để trống")
        String reason,
        @NotNull(message = "Ngày bắt đầu bảo trì là bắt buộc")
        LocalDate startDate,
        // S1-10 AC3: bắt buộc khoảng ngày dự kiến (V5 rooms_maintenance_details_check cũng yêu cầu)
        @NotNull(message = "Ngày kết thúc bảo trì dự kiến là bắt buộc")
        LocalDate endDate
) {
}