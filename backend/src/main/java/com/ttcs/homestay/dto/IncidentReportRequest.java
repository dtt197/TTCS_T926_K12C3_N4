package com.ttcs.homestay.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request báo cáo sự cố. {@code description} là tên property API; {@code note}
 * vẫn được chấp nhận như alias để tương thích với client cũ.
 */
public record IncidentReportRequest(
        @NotBlank(message = "Mức độ sự cố là bắt buộc")
        @Pattern(regexp = "NHE|NGHIEM_TRONG", message = "Mức độ chỉ nhận NHE hoặc NGHIEM_TRONG")
        String severity,

        @JsonAlias("note")
        @NotBlank(message = "Mô tả sự cố không được để trống")
        @Size(max = 2000, message = "Mô tả sự cố tối đa 2000 ký tự")
        String description
) {
    public IncidentReportRequest {
        severity = severity == null ? null : severity.trim();
        description = description == null ? null : description.trim();
    }

    /**
     * Tạm giữ constructor cũ để các caller Java hiện tại tiếp tục compile.
     * Constructor này không tạo request hợp lệ do severity hiện đã bắt buộc.
     */
    @Deprecated
    public IncidentReportRequest(String note) {
        this(null, note);
    }

}
