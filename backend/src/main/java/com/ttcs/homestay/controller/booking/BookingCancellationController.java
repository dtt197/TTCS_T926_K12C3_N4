package com.ttcs.homestay.controller.booking;

import com.ttcs.homestay.dto.booking.BookingCancellationResponse;
import com.ttcs.homestay.dto.booking.CancelBookingRequest;
import com.ttcs.homestay.dto.booking.CancellationPreviewResponse;
import com.ttcs.homestay.service.BookingCancellationService;
import com.ttcs.homestay.service.CancellationEmailService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * S3-05: xem trước hoàn cọc và xác nhận huỷ booking (chọn lý do, hệ thống tự tính hoàn cọc,
 * ghi người huỷ và thời điểm huỷ).
 * S4-05 Lát 2: sau khi huỷ đã lưu, gửi email huỷ cho khách.
 */
@RestController
@RequestMapping("/api/bookings")
public class BookingCancellationController {

    private final BookingCancellationService cancellationService;
    private final CancellationEmailService cancellationEmailService;

    public BookingCancellationController(BookingCancellationService cancellationService,
            CancellationEmailService cancellationEmailService) {
        this.cancellationService = cancellationService;
        this.cancellationEmailService = cancellationEmailService;
    }

    @GetMapping("/{bookingCode}/cancellation-preview")
    public CancellationPreviewResponse preview(@PathVariable String bookingCode) {
        return cancellationService.preview(bookingCode);
    }

    @PostMapping("/{bookingCode}/cancel")
    public BookingCancellationResponse cancel(
            @PathVariable String bookingCode,
            @Valid @RequestBody CancelBookingRequest request) {
        // cancel() đã commit khi trả về; nếu huỷ lỗi (409, 400...) ngoại lệ bay ra và KHÔNG gửi email.
        BookingCancellationResponse response = cancellationService.cancel(bookingCode, request);
        cancellationEmailService.sendCancellationEmail(bookingCode);
        return response;
    }
}