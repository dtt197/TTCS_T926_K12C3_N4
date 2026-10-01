package com.ttcs.homestay.dto.pricing;

import java.time.LocalDate;

/**
 * S2-02: giá của một đêm. label là tên đợt giá đè, "Cuối tuần" hoặc "Ngày thường".
 * price chỉ trống ở màn hình xem trước khi loại phòng chưa khai báo giá đó (Lát 4).
 */
public record NightlyPrice(LocalDate date, PriceType priceType, String label, Long price) {
}