package com.ttcs.homestay.controller.booking;

import com.ttcs.homestay.dto.booking.CancellationPreviewResponse;
import com.ttcs.homestay.service.BookingCancellationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * S3-05: xem trước hoàn cọc khi huỷ. Việc huỷ thật chỉ có một đường duy nhất:
 * PUT /api/bookings/{id}/cancel của S3-02 (BookingController), nơi ghi người huỷ, thời điểm huỷ.
 */
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
}