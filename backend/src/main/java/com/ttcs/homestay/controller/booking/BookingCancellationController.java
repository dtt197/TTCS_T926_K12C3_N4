package com.ttcs.homestay.controller.booking;

import com.ttcs.homestay.dto.booking.BookingCancellationResponse;
import com.ttcs.homestay.dto.booking.CancelBookingRequest;
import com.ttcs.homestay.dto.booking.CancellationPreviewResponse;
import com.ttcs.homestay.service.BookingCancellationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** S3-05: xem trước và xác nhận huỷ booking. */
@RestController
@RequestMapping("/api/bookings")
public class BookingCancellationController {

    private final BookingCancellationService cancellationService;

    public BookingCancellationController(BookingCancellationService cancellationService) {
        this.cancellationService = cancellationService;
    }

    @GetMapping("/{bookingCode}/cancellation-preview")
    public CancellationPreviewResponse preview(@PathVariable String bookingCode) {
        return cancellationService.preview(bookingCode);
    }

    @PostMapping("/{bookingCode}/cancel")
    public BookingCancellationResponse cancel(
            @PathVariable String bookingCode,
            @Valid @RequestBody CancelBookingRequest request) {
        return cancellationService.cancel(bookingCode, request);
    }
}