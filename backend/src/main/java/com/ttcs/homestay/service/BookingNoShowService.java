package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.dto.booking.NoShowEligibilityResponse;
import com.ttcs.homestay.entity.AppointmentActionLog;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Customer;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.AppointmentActionLogRepository;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.CustomerRepository;
import com.ttcs.homestay.repository.UserRepository;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingNoShowService {

    public static final ZoneId ZONE_VN = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final LocalTime DEFAULT_CHECK_IN_TIME = LocalTime.of(14, 0);
    public static final int ELIGIBLE_DELAY_MINUTES = 30;

    private static final Set<BookingStatus> CHECKED_IN_STATUSES = EnumSet.of(
            BookingStatus.DA_NHAN_PHONG,
            BookingStatus.DA_TRA_PHONG
    );

    private final BookingRepository bookingRepository;
    private final CustomerRepository customerRepository;
    private final AppointmentActionLogRepository actionLogRepository;
    private final UserRepository userRepository;
    private final OperatingSettingsService settingsService;
    private final AuditLogService auditLogService;

    public BookingNoShowService(
            BookingRepository bookingRepository,
            CustomerRepository customerRepository,
            AppointmentActionLogRepository actionLogRepository,
            UserRepository userRepository,
            OperatingSettingsService settingsService,
            AuditLogService auditLogService) {
        this.bookingRepository = bookingRepository;
        this.customerRepository = customerRepository;
        this.actionLogRepository = actionLogRepository;
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.auditLogService = auditLogService;
    }

    public LocalTime resolveCheckInTime(Booking booking) {
        if (booking.getRoomType() != null && booking.getRoomType().getCheckInTime() != null) {
            return booking.getRoomType().getCheckInTime();
        }
        try {
            OperatingSettings settings = settingsService.findEffectiveAt(
                    booking.getCreatedAt() != null ? booking.getCreatedAt() : OffsetDateTime.now());
            if (settings != null && settings.getCheckInTime() != null) {
                return settings.getCheckInTime();
            }
        } catch (Exception ignored) {
            // fallback
        }
        return DEFAULT_CHECK_IN_TIME;
    }

    public OffsetDateTime resolveScheduledCheckIn(Booking booking) {
        LocalTime time = resolveCheckInTime(booking);
        return booking.getCheckInDate().atTime(time).atZone(ZONE_VN).toOffsetDateTime();
    }

    @Transactional(readOnly = true)
    public NoShowEligibilityResponse checkEligibility(Long bookingId) {
        return checkEligibility(bookingId, OffsetDateTime.now(ZONE_VN));
    }

    @Transactional(readOnly = true)
    public NoShowEligibilityResponse checkEligibility(Long bookingId, OffsetDateTime evaluationTime) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch hẹn / booking"));

        OffsetDateTime now = evaluationTime != null ? evaluationTime : OffsetDateTime.now(ZONE_VN);
        OffsetDateTime scheduledCheckIn = resolveScheduledCheckIn(booking);
        OffsetDateTime eligibleAfter = scheduledCheckIn.plusMinutes(ELIGIBLE_DELAY_MINUTES);

        boolean checkedIn = CHECKED_IN_STATUSES.contains(booking.getStatus());
        if (checkedIn) {
            return new NoShowEligibilityResponse(
                    booking.getId(),
                    booking.getBookingCode(),
                    false,
                    "Lịch hẹn đã check-in, không thể đánh dấu khách không đến",
                    scheduledCheckIn,
                    eligibleAfter,
                    now,
                    booking.getStatus().name(),
                    true
            );
        }

        if (booking.getStatus() == BookingStatus.DA_HUY
                || booking.getStatus() == BookingStatus.DA_HET_HAN
                || booking.getStatus() == BookingStatus.NO_SHOW) {
            return new NoShowEligibilityResponse(
                    booking.getId(),
                    booking.getBookingCode(),
                    false,
                    "Lịch hẹn đã kết thúc hoặc đã bị đánh dấu trước đó",
                    scheduledCheckIn,
                    eligibleAfter,
                    now,
                    booking.getStatus().name(),
                    false
            );
        }

        if (now.isBefore(eligibleAfter)) {
            return new NoShowEligibilityResponse(
                    booking.getId(),
                    booking.getBookingCode(),
                    false,
                    "Chưa đủ điều kiện: Chỉ được đánh dấu sau giờ hẹn 30 phút",
                    scheduledCheckIn,
                    eligibleAfter,
                    now,
                    booking.getStatus().name(),
                    false
            );
        }

        return new NoShowEligibilityResponse(
                booking.getId(),
                booking.getBookingCode(),
                true,
                "Đủ điều kiện đánh dấu khách không đến",
                scheduledCheckIn,
                eligibleAfter,
                now,
                booking.getStatus().name(),
                false
        );
    }

    @Transactional
    public BookingResponse markNoShow(Long bookingId) {
        return markNoShow(bookingId, OffsetDateTime.now(ZONE_VN), null);
    }

    @Transactional
    public BookingResponse markNoShow(Long bookingId, OffsetDateTime evaluationTime, String ipAddress) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch hẹn / booking"));

        OffsetDateTime now = evaluationTime != null ? evaluationTime : OffsetDateTime.now(ZONE_VN);
        OffsetDateTime scheduledCheckIn = resolveScheduledCheckIn(booking);
        OffsetDateTime eligibleAfter = scheduledCheckIn.plusMinutes(ELIGIBLE_DELAY_MINUTES);

        if (CHECKED_IN_STATUSES.contains(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lịch hẹn đã check-in, không thể đánh dấu khách không đến");
        }

        if (booking.getStatus() == BookingStatus.DA_HUY
                || booking.getStatus() == BookingStatus.DA_HET_HAN
                || booking.getStatus() == BookingStatus.NO_SHOW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lịch hẹn đã bị huỷ hoặc đã đánh dấu không đến");
        }

        if (now.isBefore(eligibleAfter)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chưa đủ điều kiện: Chỉ được đánh dấu sau giờ hẹn 30 phút");
        }

        // Cập nhật trạng thái booking sang NO_SHOW (tự động giải phóng phòng vì không còn trong OCCUPYING_STATUSES)
        booking.setStatus(BookingStatus.NO_SHOW);
        Booking saved = bookingRepository.save(booking);

        // Cập nhật / tạo thông tin khách hàng và tăng no_show_count
        updateCustomerNoShowCount(booking);

        // Lưu lịch sử thao tác vào bảng appointment_action_logs
        Long receptionistId = resolveCurrentUserId();
        String actorEmail = resolveCurrentUserEmail();
        AppointmentActionLog actionLog = new AppointmentActionLog(booking.getId(), receptionistId, "NO_SHOW");
        actionLogRepository.save(actionLog);

        // Ghi audit log
        auditLogService.recordSensitiveAction(
                receptionistId,
                actorEmail,
                null,
                "Booking " + saved.getBookingCode(),
                "BOOKING_NO_SHOW",
                ipAddress
        );

        return BookingResponse.from(saved);
    }

    private void updateCustomerNoShowCount(Booking booking) {
        Customer customer = null;
        if (booking.getGuestPhone() != null && !booking.getGuestPhone().isBlank()) {
            customer = customerRepository.findFirstByPhone(booking.getGuestPhone().trim()).orElse(null);
        }
        if (customer == null && booking.getGuestEmail() != null && !booking.getGuestEmail().isBlank()) {
            customer = customerRepository.findFirstByEmail(booking.getGuestEmail().trim()).orElse(null);
        }

        if (customer != null) {
            customer.setNoShowCount(customer.getNoShowCount() + 1);
            if (booking.getGuestName() != null && !booking.getGuestName().isBlank()) {
                customer.setFullName(booking.getGuestName().trim());
            }
            customerRepository.save(customer);
        } else {
            Customer newCustomer = new Customer(
                    booking.getGuestName(),
                    booking.getGuestPhone(),
                    booking.getGuestEmail()
            );
            newCustomer.setNoShowCount(1);
            customerRepository.save(newCustomer);
        }
    }

    private Long resolveCurrentUserId() {
        String email = resolveCurrentUserEmail();
        if (email == null) return null;
        return userRepository.findByEmail(email).map(User::getId).orElse(null);
    }

    private String resolveCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return null;
        if (authentication instanceof JwtAuthenticationToken token) {
            String email = token.getToken().getClaimAsString("email");
            if (email != null && !email.isBlank()) {
                return email;
            }
        }
        return authentication.getName();
    }
}
