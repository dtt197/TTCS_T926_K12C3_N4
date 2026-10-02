package com.ttcs.homestay.controller.booking;

import com.ttcs.homestay.dto.booking.GuestBookingRequest;
import com.ttcs.homestay.dto.booking.GuestBookingResponse;
import com.ttcs.homestay.dto.booking.PublicRoomTypeOption;
import com.ttcs.homestay.service.GuestBookingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** S2-07 Lát 1: API công khai cho khách đặt phòng, không cần đăng nhập (khai báo trong SecurityConfig). */
@RestController
@RequestMapping("/api/public")
public class PublicBookingController {

    private final GuestBookingService guestBookingService;

    public PublicBookingController(GuestBookingService guestBookingService) {
        this.guestBookingService = guestBookingService;
    }

    @GetMapping("/room-types")
    public List<PublicRoomTypeOption> listRoomTypes() {
        return guestBookingService.listBookableRoomTypes();
    }

    @PostMapping("/bookings")
    public ResponseEntity<GuestBookingResponse> createBooking(@Valid @RequestBody GuestBookingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(guestBookingService.createGuestBooking(request));
    }
}