package com.ttcs.homestay.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * S3-09: Buồng phòng báo sự cố cho phòng TRONG_BAN.
 * Chỉ cần ghi chú sự cố (không cần ngày bảo trì – lễ tân xử lý sau).
 */
public record IncidentReportRequest(
        @NotBlank(message = "Ghi chú sự cố không được để trống")
        String note
) {}
