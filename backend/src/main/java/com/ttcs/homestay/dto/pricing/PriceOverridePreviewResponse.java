package com.ttcs.homestay.dto.pricing;

import java.util.List;

/**
 * S2-02 Lát 4: bảng xem trước giá từng đêm.
 * currentTotal trống nếu có đêm chưa khai báo giá; newTotal trống nếu chưa nhập giá;
 * conflict là thông báo trùng ngày với đợt khác (trống nếu không trùng).
 */
public record PriceOverridePreviewResponse(
        int nights,
        List<PreviewNight> nightlyPrices,
        Long currentTotal,
        Long newTotal,
        String conflict) {
}