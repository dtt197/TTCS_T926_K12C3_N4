package com.ttcs.homestay.dto.pricing;

import java.time.LocalDate;

/** S2-02: giá của một đêm. label là tên đợt giá đè, "Cuối tuần" hoặc "Ngày thường". */
public record NightlyPrice(LocalDate date, PriceType priceType, String label, long price) {
}