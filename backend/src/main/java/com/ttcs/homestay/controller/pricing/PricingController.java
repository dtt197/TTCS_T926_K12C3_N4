package com.ttcs.homestay.controller.pricing;

import com.ttcs.homestay.dto.pricing.PriceQuoteResponse;
import com.ttcs.homestay.service.PricingService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** S2-02 Lát 2: xem giá từng đêm của một loại phòng trong khoảng ngày nhận - trả phòng. */
@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    /** Ví dụ: /api/pricing/quote?roomTypeId=1&checkIn=2027-04-28&checkOut=2027-05-03 */
    @GetMapping("/quote")
    public PriceQuoteResponse quote(
            @RequestParam Long roomTypeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {
        return pricingService.quote(roomTypeId, checkIn, checkOut);
    }
}