package com.ttcs.homestay.dto;

import com.ttcs.homestay.entity.IssuedInvoice;
import java.time.OffsetDateTime;

public record InvoiceCancellationResponse(
        String invoiceNumber,
        IssuedInvoice.Status status,
        OffsetDateTime cancelledAt,
        String cancellationReason) {

    public static InvoiceCancellationResponse from(IssuedInvoice invoice) {
        return new InvoiceCancellationResponse(invoice.getInvoiceNumber(), invoice.getStatus(),
                invoice.getCancelledAt(), invoice.getCancellationReason());
    }
}
