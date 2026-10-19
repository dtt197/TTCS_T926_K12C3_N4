package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.booking.GuestDocumentResponse;
import com.ttcs.homestay.dto.booking.GuestIdentityResponse;
import com.ttcs.homestay.entity.BookingGuest;
import com.ttcs.homestay.repository.BookingGuestRepository;
import com.ttcs.homestay.repository.BookingRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Bảo vệ dữ liệu cá nhân (NFR) + S1-05: số giấy tờ tuỳ thân của khách mặc định chỉ hiện 4 ký tự cuối;
 * xem đầy đủ (chỉ Lễ tân, Chủ homestay - phân quyền ở ApiSecurityPolicyCatalog) luôn ghi nhật ký
 * người xem, thời điểm và địa chỉ IP.
 */
@Service
public class GuestDocumentService {

    static final String VIEW_ACTION = "GUEST_DOCUMENT_VIEWED";

    private final BookingRepository bookingRepository;
    private final BookingGuestRepository guestRepository;
    private final AuditLogService auditLogService;

    public GuestDocumentService(BookingRepository bookingRepository,
            BookingGuestRepository guestRepository,
            AuditLogService auditLogService) {
        this.bookingRepository = bookingRepository;
        this.guestRepository = guestRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<GuestDocumentResponse> listMasked(Long bookingId) {
        if (!bookingRepository.existsById(bookingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy booking.");
        }
        return guestRepository.findAllByBookingIdOrderByGuestOrderAsc(bookingId).stream()
                .map(guest -> new GuestDocumentResponse(
                        guest.getId(),
                        guest.getFullName(),
                        guest.getGuestOrder() == 0,
                        mask(guest.getIdentityNumber()),
                        hasText(guest.getIdentityNumber())))
                .toList();
    }

    @Transactional
    public GuestIdentityResponse revealIdentity(Long bookingId, Long guestId,
            Long actorUserId, String actorEmail, String ipAddress) {
        BookingGuest guest = guestRepository.findById(guestId)
                .filter(found -> found.getBooking() != null && bookingId.equals(found.getBooking().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy khách của booking này."));
        if (!hasText(guest.getIdentityNumber())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Khách này chưa có số giấy tờ.");
        }
        // Ghi nhật ký trong cùng giao dịch: không ghi được nhật ký thì không trả số giấy tờ.
        auditLogService.recordSensitiveActionInCurrentTransaction(
                actorUserId, actorEmail, null,
                "Booking " + guest.getBooking().getBookingCode() + " - " + guest.getFullName(),
                VIEW_ACTION, ipAddress);
        return new GuestIdentityResponse(guest.getId(), guest.getFullName(), guest.getIdentityNumber());
    }

    /** Chỉ giữ 4 ký tự cuối, ví dụ 001204567890 → ********7890. */
    static String mask(String identityNumber) {
        if (!hasText(identityNumber)) {
            return null;
        }
        String value = identityNumber.trim();
        if (value.length() <= 4) {
            return "*".repeat(value.length());
        }
        return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
