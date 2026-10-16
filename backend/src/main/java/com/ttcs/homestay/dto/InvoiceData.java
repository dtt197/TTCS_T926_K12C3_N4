package com.ttcs.homestay.dto;

import com.ttcs.homestay.entity.BookingSettlement;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;

/** Immutable, assembled data for a single-booking invoice. */
public record InvoiceData(
        long bookingId, String bookingCode, String guestName, String guestPhone,
        String companyName, String companyTaxCode, Homestay homestay, Room room,
        LocalDate checkInDate, LocalDate checkOutDate, long nights,
        List<LineItem> lineItems, BigDecimal finalTotal, BigDecimal depositTotal,
        BookingSettlement.SettlementType settlementType, BigDecimal settlementAmount,
        BigDecimal remainingAmount, OffsetDateTime checkedOutAt, boolean contactInfoSupplementedAtExport,
        String invoiceNumber, LocalDate issuedDate) {

    public InvoiceData(long bookingId, String bookingCode, String guestName, String guestPhone,
            String companyName, String companyTaxCode, Homestay homestay, Room room,
            LocalDate checkInDate, LocalDate checkOutDate, long nights,
            List<LineItem> lineItems, BigDecimal finalTotal, BigDecimal depositTotal,
            BookingSettlement.SettlementType settlementType, BigDecimal settlementAmount,
            BigDecimal remainingAmount, OffsetDateTime checkedOutAt, boolean contactInfoSupplementedAtExport) {
        this(bookingId, bookingCode, guestName, guestPhone, companyName, companyTaxCode, homestay, room,
                checkInDate, checkOutDate, nights, lineItems, finalTotal, depositTotal, settlementType,
                settlementAmount, remainingAmount, checkedOutAt, contactInfoSupplementedAtExport, null, null);
    }

    public InvoiceData withIssuance(String number, LocalDate date) {
        return new InvoiceData(bookingId, bookingCode, guestName, guestPhone, companyName, companyTaxCode,
                homestay, room, checkInDate, checkOutDate, nights, lineItems, finalTotal, depositTotal,
                settlementType, settlementAmount, remainingAmount, checkedOutAt, contactInfoSupplementedAtExport,
                number, date);
    }

    public InvoiceData { lineItems = List.copyOf(lineItems); }
    public record Homestay(String name, String address, String phone, String logoUrl, String taxCode) {}
    public record Room(Long id, String number, String typeName) {}
    public record LineItem(String code, String description, BigDecimal amount) {}
}
