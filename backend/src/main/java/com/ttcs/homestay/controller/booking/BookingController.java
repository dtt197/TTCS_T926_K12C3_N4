package com.ttcs.homestay.controller.booking;

import com.ttcs.homestay.dto.booking.BookingCreateRequest;
import com.ttcs.homestay.dto.booking.BookingListItemResponse;
import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.dto.booking.PageResponse;
import com.ttcs.homestay.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody BookingCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookingService.createBooking(request));
    }

    @GetMapping("/latest")
    public List<BookingListItemResponse> getBookings() {
        return bookingService.getLatestBookings();
    }

    @GetMapping
    public PageResponse<BookingResponse> list(@RequestParam(defaultValue = "0") int page) {
        return PageResponse.from(bookingService.findPage(page).map(BookingResponse::from));
    }
}