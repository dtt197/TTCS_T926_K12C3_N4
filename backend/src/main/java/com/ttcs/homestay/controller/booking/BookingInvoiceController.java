package com.ttcs.homestay.controller.booking;

import com.ttcs.homestay.dto.InvoiceData;
import com.ttcs.homestay.service.InvoiceDataService;
import com.ttcs.homestay.service.InvoicePdfService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bookings")
public class BookingInvoiceController {
    private final InvoiceDataService invoiceDataService;
    private final InvoicePdfService invoicePdfService;

    public BookingInvoiceController(InvoiceDataService invoiceDataService, InvoicePdfService invoicePdfService) {
        this.invoiceDataService = invoiceDataService;
        this.invoicePdfService = invoicePdfService;
    }

    @GetMapping(value = "/{id}/invoice.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadInvoice(@PathVariable long id) {
        InvoiceData data = invoiceDataService.getInvoiceData(id);
        String code = data.bookingCode() == null ? "booking-" + id
                : data.bookingCode().replaceAll("[^A-Za-z0-9._-]", "_");
        if (code.isBlank() || code.equals(".") || code.equals("..")) code = "booking-" + id;
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("invoice_" + code + ".pdf", StandardCharsets.UTF_8).build().toString())
                .body(invoicePdfService.generate(data));
    }
}
