package com.ttcs.homestay.controller.booking;

import com.ttcs.homestay.entity.IssuedInvoice;
import com.ttcs.homestay.dto.InvoiceCancellationRequest;
import com.ttcs.homestay.dto.InvoiceCancellationResponse;
import com.ttcs.homestay.service.InvoiceIssuanceService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/bookings")
public class BookingInvoiceController {
    private final InvoiceIssuanceService invoiceIssuanceService;

    public BookingInvoiceController(InvoiceIssuanceService invoiceIssuanceService) {
        this.invoiceIssuanceService = invoiceIssuanceService;
    }

    @GetMapping(value = "/{id}/invoice.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable long id) {
        IssuedInvoice invoice = invoiceIssuanceService.downloadOrIssue(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("invoice_" + invoice.getInvoiceNumber() + ".pdf", StandardCharsets.UTF_8).build().toString())
                .body(invoice.getPdfBytes());
    }

    @PutMapping("/{id}/invoice/cancel")
    public InvoiceCancellationResponse cancelInvoice(@PathVariable long id,
            @Valid @RequestBody InvoiceCancellationRequest request) {
        return InvoiceCancellationResponse.from(invoiceIssuanceService.cancel(id, request));
    }
}
