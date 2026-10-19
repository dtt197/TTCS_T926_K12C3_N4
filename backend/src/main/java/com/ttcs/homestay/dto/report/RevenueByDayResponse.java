package com.ttcs.homestay.dto.report;

import java.time.LocalDate;

/** Revenue recognized on a booking's checkout date. */
public record RevenueByDayResponse(LocalDate date, long totalRevenue) {
}
