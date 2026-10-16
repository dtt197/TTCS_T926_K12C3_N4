package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.booking.AppointmentActionLogResponse;
import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.dto.booking.CustomerNoShowResponse;
import com.ttcs.homestay.dto.booking.NoShowEligibilityResponse;
import com.ttcs.homestay.dto.booking.NoShowUndoEligibilityResponse;
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
import java.time.Duration;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
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
    public static final int UNDO_WINDOW_MINUTES = 15;

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

        BookingStatus previousStatus = booking.getStatus();

        // Cập nhật trạng thái booking sang NO_SHOW (tự động giải phóng phòng vì không còn trong OCCUPYING_STATUSES)
        booking.setStatus(BookingStatus.NO_SHOW);
        Booking saved = bookingRepository.save(booking);

        // Cập nhật / tạo thông tin khách hàng và tăng no_show_count
        updateCustomerNoShowCount(booking);

        // Lưu lịch sử thao tác vào bảng appointment_action_logs (S4-04 / SCRUM-151)
        Long receptionistId = resolveCurrentUserId();
        String actorEmail = resolveCurrentUserEmail();
        AppointmentActionLog actionLog = new AppointmentActionLog(
                booking.getId(),
                receptionistId,
                "MARK_NO_SHOW",
                previousStatus != null ? previousStatus.name() : "DA_XAC_NHAN"
        );
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

    @Transactional(readOnly = true)
    public CustomerNoShowResponse getCustomerNoShowHistory(String phone) {
        if (phone == null || phone.isBlank()) {
            return new CustomerNoShowResponse(null, null, 0, false, null);
        }
        String cleanPhone = phone.trim();
        Optional<Customer> optionalCustomer = customerRepository.findFirstByPhone(cleanPhone);
        if (optionalCustomer.isEmpty()) {
            return new CustomerNoShowResponse(cleanPhone, null, 0, false, null);
        }

        Customer customer = optionalCustomer.get();
        int count = customer.getNoShowCount();
        boolean hasWarning = count > 0;
        String warningMessage = hasWarning
                ? "⚠️ Khách hàng này đã bỏ hẹn " + count + " lần"
                : null;

        return new CustomerNoShowResponse(
                customer.getPhone(),
                customer.getFullName(),
                count,
                hasWarning,
                warningMessage
        );
    }

    public void updateCustomerNoShowCount(Booking booking) {
        String phone = booking.getGuestPhone() != null ? booking.getGuestPhone().trim() : null;
        Customer customer = null;
        if (phone != null && !phone.isBlank()) {
            customer = customerRepository.findFirstByPhone(phone).orElse(null);
        }
        if (customer == null && booking.getGuestEmail() != null && !booking.getGuestEmail().isBlank()) {
            customer = customerRepository.findFirstByEmail(booking.getGuestEmail().trim()).orElse(null);
        }

        if (customer != null) {
            customer.setNoShowCount(customer.getNoShowCount() + 1);
            if ((customer.getFullName() == null || customer.getFullName().isBlank()) && booking.getGuestName() != null) {
                customer.setFullName(booking.getGuestName().trim());
            }
            if ((customer.getPhone() == null || customer.getPhone().isBlank()) && phone != null) {
                customer.setPhone(phone);
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

    @Transactional(readOnly = true)
    public NoShowUndoEligibilityResponse checkUndoEligibility(Long bookingId) {
        return checkUndoEligibility(bookingId, OffsetDateTime.now(ZONE_VN));
    }

    @Transactional(readOnly = true)
    public NoShowUndoEligibilityResponse checkUndoEligibility(Long bookingId, OffsetDateTime evaluationTime) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch hẹn / booking"));

        if (booking.getStatus() != BookingStatus.NO_SHOW) {
            return new NoShowUndoEligibilityResponse(
                    booking.getId(),
                    booking.getBookingCode(),
                    false,
                    "Lịch hẹn không ở trạng thái không đến",
                    null,
                    null,
                    0,
                    null,
                    null
            );
        }

        AppointmentActionLog noShowLog = findLatestNoShowLog(bookingId);
        if (noShowLog == null) {
            return new NoShowUndoEligibilityResponse(
                    booking.getId(),
                    booking.getBookingCode(),
                    false,
                    "Không tìm thấy lịch sử đánh dấu không đến",
                    null,
                    null,
                    0,
                    null,
                    null
            );
        }

        OffsetDateTime now = evaluationTime != null ? evaluationTime : OffsetDateTime.now(ZONE_VN);
        OffsetDateTime markedAt = noShowLog.getCreatedAt();
        OffsetDateTime expiresAt = markedAt.plusMinutes(UNDO_WINDOW_MINUTES);
        long remainingSeconds = Duration.between(now, expiresAt).getSeconds();
        String receptionistName = resolveUserName(noShowLog.getReceptionistId());

        if (remainingSeconds <= 0) {
            return new NoShowUndoEligibilityResponse(
                    booking.getId(),
                    booking.getBookingCode(),
                    false,
                    "Đã quá thời hạn 15 phút để hoàn tác thao tác không đến",
                    markedAt,
                    expiresAt,
                    0,
                    noShowLog.getReceptionistId(),
                    receptionistName
            );
        }

        return new NoShowUndoEligibilityResponse(
                booking.getId(),
                booking.getBookingCode(),
                true,
                "Có thể hoàn tác",
                markedAt,
                expiresAt,
                remainingSeconds,
                noShowLog.getReceptionistId(),
                receptionistName
        );
    }

    @Transactional
    public BookingResponse undoNoShow(Long bookingId) {
        return undoNoShow(bookingId, OffsetDateTime.now(ZONE_VN), null);
    }

    @Transactional
    public BookingResponse undoNoShow(Long bookingId, OffsetDateTime evaluationTime, String ipAddress) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy lịch hẹn / booking"));

        if (booking.getStatus() != BookingStatus.NO_SHOW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Lịch hẹn không ở trạng thái không đến (No-show)");
        }

        AppointmentActionLog noShowLog = findLatestNoShowLog(bookingId);
        if (noShowLog == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Không tìm thấy vết thao tác đánh dấu không đến");
        }

        OffsetDateTime now = evaluationTime != null ? evaluationTime : OffsetDateTime.now(ZONE_VN);
        OffsetDateTime markedAt = noShowLog.getCreatedAt();
        OffsetDateTime expiresAt = markedAt.plusMinutes(UNDO_WINDOW_MINUTES);

        if (now.isAfter(expiresAt)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Đã quá thời hạn 15 phút để hoàn tác thao tác không đến"
            );
        }

        // Khôi phục lại trạng thái lịch hẹn ban đầu
        BookingStatus restoredStatus = BookingStatus.DA_XAC_NHAN;
        if (noShowLog.getPreviousStatus() != null && !noShowLog.getPreviousStatus().isBlank()) {
            try {
                restoredStatus = BookingStatus.valueOf(noShowLog.getPreviousStatus());
            } catch (Exception ignored) {
            }
        }
        booking.setStatus(restoredStatus);

        // Tính toán khôi phục lại phòng và lịch làm việc của nhân viên
        if (booking.getRoom() != null) {
            List<Booking> conflicts = bookingRepository.findRoomConflicts(
                    booking.getRoom().getId(),
                    booking.getId(),
                    booking.getCheckInDate(),
                    booking.getCheckOutDate(),
                    RoomAvailabilityService.OCCUPYING_STATUSES
            );
            if (!conflicts.isEmpty()) {
                booking.setRoom(null);
            }
        }

        Booking saved = bookingRepository.save(booking);

        // Trừ lại 1 lần không đến trong hồ sơ khách
        decrementCustomerNoShowCount(booking);

        // Ghi nhật ký vào bảng appointment_action_logs với action_type = 'UNDO_NO_SHOW'
        Long receptionistId = resolveCurrentUserId();
        String actorEmail = resolveCurrentUserEmail();
        AppointmentActionLog undoLog = new AppointmentActionLog(
                booking.getId(),
                receptionistId,
                "UNDO_NO_SHOW",
                "NO_SHOW"
        );
        actionLogRepository.save(undoLog);

        // Ghi audit log
        auditLogService.recordSensitiveAction(
                receptionistId,
                actorEmail,
                null,
                "Booking " + saved.getBookingCode(),
                "BOOKING_UNDO_NO_SHOW",
                ipAddress
        );

        return BookingResponse.from(saved);
    }

    private AppointmentActionLog findLatestNoShowLog(Long bookingId) {
        List<AppointmentActionLog> logs = actionLogRepository.findByAppointmentIdOrderByCreatedAtDesc(bookingId);
        for (AppointmentActionLog log : logs) {
            if ("MARK_NO_SHOW".equalsIgnoreCase(log.getActionType()) || "NO_SHOW".equalsIgnoreCase(log.getActionType())) {
                return log;
            }
        }
        return null;
    }

    public void decrementCustomerNoShowCount(Booking booking) {
        Customer customer = null;
        if (booking.getGuestPhone() != null && !booking.getGuestPhone().isBlank()) {
            customer = customerRepository.findFirstByPhone(booking.getGuestPhone().trim()).orElse(null);
        }
        if (customer == null && booking.getGuestEmail() != null && !booking.getGuestEmail().isBlank()) {
            customer = customerRepository.findFirstByEmail(booking.getGuestEmail().trim()).orElse(null);
        }

        if (customer != null) {
            customer.setNoShowCount(Math.max(0, customer.getNoShowCount() - 1));
            customerRepository.save(customer);
        }
    }

    @Transactional(readOnly = true)
    public List<AppointmentActionLogResponse> getActionLogs(Long bookingId) {
        return actionLogRepository.findByAppointmentIdOrderByCreatedAtDesc(bookingId).stream()
                .map(log -> new AppointmentActionLogResponse(
                        log.getId(),
                        log.getAppointmentId(),
                        log.getReceptionistId(),
                        resolveUserName(log.getReceptionistId()),
                        log.getActionType(),
                        log.getPreviousStatus(),
                        log.getCreatedAt()
                ))
                .toList();
    }

    public String resolveUserName(Long userId) {
        if (userId == null) return "Lễ tân";
        return userRepository.findById(userId)
                .map(u -> (u.getFullName() != null && !u.getFullName().isBlank()) ? u.getFullName() : u.getEmail())
                .orElse("Lễ tân");
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
