package com.ttcs.homestay.dto;

import com.ttcs.homestay.entity.BookingSettlement;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Immutable, assembled data for a single-booking invoice. */
public record InvoiceData(
        long bookingId, String bookingCode, String guestName, String guestPhone,
        String companyName, String companyTaxCode, Homestay homestay, Room room,
        LocalDate checkInDate, LocalDate checkOutDate, long nights,
        List<LineItem> lineItems, BigDecimal finalTotal, BigDecimal depositTotal,
        BookingSettlement.SettlementType settlementType, BigDecimal settlementAmount,
        BigDecimal remainingAmount, OffsetDateTime checkedOutAt, boolean contactInfoSupplementedAtExport) {

    public InvoiceData { lineItems = List.copyOf(lineItems); }
    public record Homestay(String name, String address, String phone, String logoUrl, String taxCode) {}
    public record Room(Long id, String number, String typeName) {}
    public record LineItem(String code, String description, BigDecimal amount) {}
}
