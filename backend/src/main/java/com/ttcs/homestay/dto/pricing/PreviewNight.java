package com.ttcs.homestay.dto.pricing;

import java.time.LocalDate;

/**
 * S2-02 Lát 4: một đêm trong bảng xem trước.
 * currentPrice là giá nếu không có đợt đang nhập (trống nếu loại phòng chưa khai báo giá đó);
 * newPrice là giá sau khi lưu (trống nếu chưa nhập giá).
 */
public record PreviewNight(
        LocalDate date,
        PriceType currentType,
        String currentLabel,
        Long currentPrice,
        Long newPrice) {
}