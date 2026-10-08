package com.ttcs.homestay.controller.booking;

import com.ttcs.homestay.dto.booking.BookingCreateRequest;
import com.ttcs.homestay.dto.booking.BookingListItemResponse;
import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.dto.booking.BookingUpdateRequest;
import com.ttcs.homestay.dto.booking.PageResponse;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.service.BookingService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
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

    @PutMapping("/{id}")
    public BookingResponse updateBooking(
            @PathVariable Long id,
            @Valid @RequestBody BookingUpdateRequest request) {
        return bookingService.updateBooking(id, request);
    }

    @PostMapping("/{id}/preview")
    public com.ttcs.homestay.dto.booking.BookingChangePreviewResponse previewChange(
            @PathVariable Long id,
            @Valid @RequestBody BookingUpdateRequest request) {
        return bookingService.previewBookingChange(id, request);
    }

    @GetMapping("/latest")
    public List<BookingListItemResponse> getBookings() {
        return bookingService.getLatestBookings();
    }

    @GetMapping
    public PageResponse<BookingResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) BookingStatus status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate checkInFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate checkInTo,
            @RequestParam(required = false) String keyword) {

        if (checkInFrom != null
                && checkInTo != null
                && checkInTo.isBefore(checkInFrom)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu");
        }

        return PageResponse.from(
                bookingService.findPage(
                        page,
                        status,
                        checkInFrom,
                        checkInTo,
                        keyword
                ).map(BookingResponse::from)
        );
    }
}