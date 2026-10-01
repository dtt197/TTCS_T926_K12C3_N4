package com.ttcs.homestay.dto.pricing;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * S2-02 Lát 4: dữ liệu đang nhập trong form (chưa lưu) để xem trước giá.
 * pricePerNight có thể trống khi chưa nhập giá; excludeId là đợt đang sửa (trống khi thêm mới).
 */
public record PriceOverridePreviewRequest(
        @NotNull(message = "Vui lòng chọn loại phòng")
        Long roomTypeId,

        @NotNull(message = "Vui lòng chọn ngày bắt đầu")
        LocalDate startDate,

        @NotNull(message = "Vui lòng chọn ngày kết thúc")
        LocalDate endDate,

        @Min(value = 1, message = "Giá một đêm phải lớn hơn 0")
        @Max(value = 100_000_000, message = "Giá một đêm tối đa 100.000.000 VND")
        Long pricePerNight,

        Long excludeId) {
}