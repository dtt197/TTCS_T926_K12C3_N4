package com.ttcs.homestay.controller.booking;

import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.dto.booking.CustomerNoShowResponse;
import com.ttcs.homestay.dto.booking.NoShowEligibilityResponse;
import com.ttcs.homestay.service.BookingNoShowService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingNoShowController {

    private final BookingNoShowService noShowService;

    public BookingNoShowController(BookingNoShowService noShowService) {
        this.noShowService = noShowService;
    }

    @GetMapping({"/api/bookings/{id}/no-show/eligibility", "/api/appointments/{id}/no-show/eligibility"})
    public NoShowEligibilityResponse checkEligibility(@PathVariable Long id) {
        return noShowService.checkEligibility(id);
    }

    @PostMapping({"/api/bookings/{id}/no-show", "/api/appointments/{id}/no-show"})
    public BookingResponse markNoShow(@PathVariable Long id, HttpServletRequest request) {
        String ipAddress = request != null ? request.getRemoteAddr() : null;
        return noShowService.markNoShow(id, null, ipAddress);
    }

    @GetMapping({
            "/api/customers/no-show-history",
            "/api/customers/lookup",
            "/api/bookings/customer-no-show",
            "/api/appointments/customer-no-show"
    })
    public CustomerNoShowResponse getCustomerNoShowHistory(@RequestParam(required = false) String phone) {
        return noShowService.getCustomerNoShowHistory(phone);
    }
}
