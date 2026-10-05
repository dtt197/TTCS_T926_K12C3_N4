package com.ttcs.homestay.controller.booking;

import com.ttcs.homestay.dto.booking.BookingLookupRequest;
import com.ttcs.homestay.dto.booking.BookingLookupResponse;
import com.ttcs.homestay.service.BookingLookupService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * S2-08: khách tra cứu booking bằng mã booking và email, không cần đăng nhập
 * (khai báo trong SecurityConfig). Dùng POST để email không nằm trên đường dẫn.
 */
@RestController
@RequestMapping("/api/public/bookings")
public class PublicBookingLookupController {

    private final BookingLookupService bookingLookupService;

    public PublicBookingLookupController(BookingLookupService bookingLookupService) {
        this.bookingLookupService = bookingLookupService;
    }

    @PostMapping("/lookup")
    public BookingLookupResponse lookup(@Valid @RequestBody BookingLookupRequest request) {
        return bookingLookupService.lookup(request.bookingCode(), request.email());
    }
}