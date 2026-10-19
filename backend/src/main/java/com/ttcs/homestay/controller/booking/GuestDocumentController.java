package com.ttcs.homestay.controller.booking;

import com.ttcs.homestay.dto.booking.GuestDocumentResponse;
import com.ttcs.homestay.dto.booking.GuestIdentityResponse;
import com.ttcs.homestay.service.GuestDocumentService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Giấy tờ khách lưu trú của booking.
 * GET: Quản trị, Chủ homestay, Lễ tân - số giấy tờ đã che (4 ký tự cuối).
 * POST .../identity-view: chỉ Chủ homestay, Lễ tân - số đầy đủ, mỗi lần xem ghi nhật ký (dùng POST vì có ghi dữ liệu).
 */
@RestController
@RequestMapping("/api/bookings/{bookingId}/guests")
public class GuestDocumentController {

    private final GuestDocumentService guestDocumentService;

    public GuestDocumentController(GuestDocumentService guestDocumentService) {
        this.guestDocumentService = guestDocumentService;
    }

    @GetMapping
    public List<GuestDocumentResponse> list(@PathVariable Long bookingId) {
        return guestDocumentService.listMasked(bookingId);
    }

    @PostMapping("/{guestId}/identity-view")
    public GuestIdentityResponse revealIdentity(@PathVariable Long bookingId,
            @PathVariable Long guestId,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest request) {
        Long actorUserId = null;
        String actorEmail = null;
        if (jwt != null) {
            try {
                actorUserId = Long.valueOf(jwt.getSubject());
            } catch (NumberFormatException ignored) {
                // subject không phải mã người dùng dạng số
            }
            actorEmail = jwt.getClaimAsString("email");
        }
        return guestDocumentService.revealIdentity(bookingId, guestId, actorUserId, actorEmail, request.getRemoteAddr());
    }
}
