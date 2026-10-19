package com.ttcs.homestay.service;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.EmailLog;
import com.ttcs.homestay.entity.EmailStatus;
import com.ttcs.homestay.entity.EmailType;
import com.ttcs.homestay.repository.BookingDepositRepository;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.EmailLogRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xử lý gửi email tiếp nhận đặt phòng và xác nhận booking cho khách hàng,
 * đồng thời ghi nhật ký vào bảng email_logs.
 * Mọi lỗi gửi email chỉ ghi log, không bao giờ làm gián đoạn transaction chính.
 */
@Service
public class BookingEmailService {

    private static final Logger log = LoggerFactory.getLogger(BookingEmailService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    /** S4-04 AC3: gửi lỗi thì tự thử lại tối đa 3 lần, mỗi lần cách nhau 5 phút. */
    static final int MAX_RETRIES = 3;
    static final Duration RETRY_DELAY = Duration.ofMinutes(5);
    /** Chỉ xét các lần lỗi gần đây (đủ cho 3 lần thử lại kể cả khi tác vụ chạy trễ). */
    private static final Duration RETRY_WINDOW = Duration.ofMinutes(30);
    private static final Set<EmailType> RETRYABLE_TYPES = Set.of(EmailType.BOOKING_CREATED, EmailType.CONFIRMATION);

    private final BookingRepository bookingRepository;
    private final EmailLogRepository emailLogRepository;
    private final MailService mailService;
    private BookingDepositRepository depositRepository;

    public BookingEmailService(BookingRepository bookingRepository,
                               EmailLogRepository emailLogRepository,
                               MailService mailService) {
        this.bookingRepository = bookingRepository;
        this.emailLogRepository = emailLogRepository;
        this.mailService = mailService;
    }

    /** Tiền cọc hiện tại, dùng khi gửi lại email xác nhận. */
    @Autowired(required = false)
    public void setDepositRepository(BookingDepositRepository depositRepository) {
        this.depositRepository = depositRepository;
    }

    /** Gửi email tiếp nhận yêu cầu đặt phòng (kèm mã booking và hạn giữ chỗ 24h). */
    public void sendBookingCreatedEmail(String bookingCode) {
        try {
            String code = bookingCode == null ? "" : bookingCode.trim().toUpperCase(Locale.ROOT);
            Booking booking = bookingRepository.findByBookingCode(code).orElse(null);
            if (booking == null) {
                log.warn("Không tìm thấy booking {} để gửi email tiếp nhận đặt phòng", code);
                return;
            }
            sendCreatedFor(booking);
        } catch (Exception e) {
            log.warn("Không gửi được email tiếp nhận cho booking {}: {}", bookingCode, e.getMessage());
        }
    }

    /** Gửi email xác nhận đặt phòng thành công (sau khi lễ tân xác nhận cọc). */
    public void sendBookingConfirmedEmail(String bookingCode, Long depositAmount) {
        try {
            String code = bookingCode == null ? "" : bookingCode.trim().toUpperCase(Locale.ROOT);
            Booking booking = bookingRepository.findByBookingCode(code).orElse(null);
            if (booking == null) {
                log.warn("Không tìm thấy booking {} để gửi email xác nhận đặt phòng", code);
                return;
            }
            sendConfirmedFor(booking, depositAmount);
        } catch (Exception e) {
            log.warn("Không gửi được email xác nhận cho booking {}: {}", bookingCode, e.getMessage());
        }
    }

    /**
     * S4-04 AC3: mỗi phút tìm email tiếp nhận/xác nhận bị lỗi và gửi lại khi đến hạn
     * (5 phút sau lần thử trước), tối đa 3 lần thử lại; mỗi lần đều ghi vào nhật ký gửi thư.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    @Transactional
    public void retryFailedEmails() {
        try {
            int retried = retryFailedEmails(OffsetDateTime.now());
            if (retried > 0) {
                log.info("Đã gửi lại {} email booking bị lỗi", retried);
            }
        } catch (Exception e) {
            log.warn("Tác vụ gửi lại email booking lỗi: {}", e.getMessage());
        }
    }

    int retryFailedEmails(OffsetDateTime now) {
        List<EmailLog> recentFailures = emailLogRepository.findByEmailTypeInAndStatusAndSentAtAfter(
                RETRYABLE_TYPES, EmailStatus.FAILED, now.minus(RETRY_WINDOW));
        Map<String, List<EmailLog>> byBookingAndType = recentFailures.stream()
                .collect(Collectors.groupingBy(entry -> entry.getBookingId() + ":" + entry.getEmailType()));
        int retried = 0;
        for (List<EmailLog> failures : byBookingAndType.values()) {
            EmailLog latest = failures.stream().max(Comparator.comparing(EmailLog::getSentAt)).orElseThrow();
            if (shouldRetry(latest, now)) {
                Booking booking = bookingRepository.findById(latest.getBookingId()).orElse(null);
                if (booking != null && stillNeedsEmail(booking, latest.getEmailType())) {
                    if (latest.getEmailType() == EmailType.BOOKING_CREATED) {
                        sendCreatedFor(booking);
                    } else {
                        sendConfirmedFor(booking, currentDeposit(booking));
                    }
                    retried++;
                }
            }
        }
        return retried;
    }

    private boolean shouldRetry(EmailLog latest, OffsetDateTime now) {
        if (latest.getSentAt().plus(RETRY_DELAY).isAfter(now)) {
            return false;
        }
        String email = latest.getRecipientEmail();
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            return false; // email sai định dạng: gửi lại cũng không được
        }
        Long bookingId = latest.getBookingId();
        EmailType type = latest.getEmailType();
        if (emailLogRepository.existsByBookingIdAndEmailTypeAndStatus(bookingId, type, EmailStatus.SENT)) {
            return false;
        }
        // Lần gửi đầu + tối đa MAX_RETRIES lần gửi lại.
        long failedAttempts = emailLogRepository.countByBookingIdAndEmailTypeAndStatus(bookingId, type, EmailStatus.FAILED);
        return failedAttempts <= MAX_RETRIES;
    }

    /** Không gửi lại thư đã lỗi thời: booking đã xác nhận thì thôi thư tiếp nhận, booking đã huỷ thì thôi thư xác nhận. */
    private static boolean stillNeedsEmail(Booking booking, EmailType type) {
        BookingStatus status = booking.getStatus();
        if (type == EmailType.BOOKING_CREATED) {
            return status == BookingStatus.CHO_XAC_NHAN;
        }
        return status == BookingStatus.DA_XAC_NHAN || status == BookingStatus.DA_NHAN_PHONG;
    }

    private Long currentDeposit(Booking booking) {
        if (depositRepository == null) {
            return 0L;
        }
        return depositRepository.findByBooking(booking)
                .map(deposit -> deposit.getAmount() == null ? 0L : deposit.getAmount().longValue())
                .orElse(0L);
    }

    private void sendCreatedFor(Booking booking) {
        String email = booking.getGuestEmail();
        // S4-04 AC4: booking không có email thì bỏ qua, không ghi lỗi.
        if (email == null || email.isBlank()) {
            return;
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            writeLog(booking, email, EmailType.BOOKING_CREATED, EmailStatus.FAILED, "Email khách không hợp lệ");
            return;
        }
        try {
            mailService.sendBookingCreatedEmail(booking);
            writeLog(booking, email.trim(), EmailType.BOOKING_CREATED, EmailStatus.SENT, null);
        } catch (Exception e) {
            log.warn("Không gửi được email tiếp nhận cho booking {}: {}", booking.getBookingCode(), e.getMessage());
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            writeLog(booking, email.trim(), EmailType.BOOKING_CREATED, EmailStatus.FAILED,
                    message.length() > 500 ? message.substring(0, 500) : message);
        }
    }

    private void sendConfirmedFor(Booking booking, Long depositAmount) {
        String email = booking.getGuestEmail();
        // S4-04 AC4: booking không có email thì bỏ qua, không ghi lỗi.
        if (email == null || email.isBlank()) {
            return;
        }
        if (!EMAIL_PATTERN.matcher(email.trim()).matches()) {
            writeLog(booking, email, EmailType.CONFIRMATION, EmailStatus.FAILED, "Email khách không hợp lệ");
            return;
        }
        try {
            mailService.sendBookingConfirmation(booking, depositAmount);
            writeLog(booking, email.trim(), EmailType.CONFIRMATION, EmailStatus.SENT, null);
        } catch (Exception e) {
            log.warn("Không gửi được email xác nhận cho booking {}: {}", booking.getBookingCode(), e.getMessage());
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            writeLog(booking, email.trim(), EmailType.CONFIRMATION, EmailStatus.FAILED,
                    message.length() > 500 ? message.substring(0, 500) : message);
        }
    }

    private void writeLog(Booking booking, String email, EmailType emailType, EmailStatus status, String error) {
        EmailLog entry = new EmailLog();
        entry.setBookingId(booking.getId());
        entry.setEmailType(emailType);
        entry.setRecipientEmail(email);
        entry.setStatus(status);
        entry.setErrorMessage(error);
        entry.setSentAt(OffsetDateTime.now());
        emailLogRepository.save(entry);
    }
}
