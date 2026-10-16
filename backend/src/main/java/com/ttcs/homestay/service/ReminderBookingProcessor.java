package com.ttcs.homestay.service;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.EmailLog;
import com.ttcs.homestay.entity.EmailStatus;
import com.ttcs.homestay.entity.EmailType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.EmailLogRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderBookingProcessor {

    private static final Logger log =
            LoggerFactory.getLogger(ReminderBookingProcessor.class);

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public enum Outcome {
        SENT,
        FAILED,
        SKIPPED
    }

    private final BookingRepository bookingRepository;
    private final EmailLogRepository emailLogRepository;
    private final MailService mailService;
    private final ReminderRunService reminderRunService;

        public ReminderBookingProcessor(
            BookingRepository bookingRepository,
            EmailLogRepository emailLogRepository,
            MailService mailService,
            ReminderRunService reminderRunService) {
        this.bookingRepository = bookingRepository;
        this.emailLogRepository = emailLogRepository;
        this.mailService = mailService;
        this.reminderRunService = reminderRunService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome process(
        Long bookingId, LocalDate expectedCheckInDate, Long runId) {
        // Mỗi booking được xử lý trong transaction riêng.
        // Lượt chạy khác phải chờ transaction này kết thúc.
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElse(null);

        if (booking == null) {
            return recordOutcome(runId, Outcome.SKIPPED);
        }

        // Đọc lại trạng thái và ngày sau khi lấy khoá.
        if (booking.getStatus() != BookingStatus.DA_XAC_NHAN
                || !expectedCheckInDate.equals(booking.getCheckInDate())) {
            return recordOutcome(runId, Outcome.SKIPPED);
        }

                // Đã gửi thành công thì không gửi lại.
        boolean alreadySent =
                emailLogRepository.existsByBookingIdAndEmailTypeAndStatus(
                        bookingId, EmailType.REMINDER, EmailStatus.SENT);

        if (alreadySent) {
            return recordOutcome(runId, Outcome.SKIPPED);
        }

        // Giả định nghiệp vụ: tối đa 3 lần gửi tổng cộng mỗi booking.
        long failedAttempts =
                emailLogRepository.countByBookingIdAndEmailTypeAndStatus(
                        bookingId, EmailType.REMINDER, EmailStatus.FAILED);

        if (failedAttempts >= 3) {
           return recordOutcome(runId, Outcome.SKIPPED);
        }

        String email = booking.getGuestEmail();
        if (email == null || !EMAIL_PATTERN.matcher(email).matches()) {
            writeLog(
                    runId,
                    booking,
                    email,
                    EmailStatus.FAILED,
                    "Email khách trống hoặc không hợp lệ");
            return recordOutcome(runId, Outcome.FAILED);
        }

        // Chỉ bắt lỗi phát sinh từ việc gửi email.
        try {
            mailService.sendCheckInReminder(booking);
        } catch (Exception e) {
            String message = e.getMessage() == null
                    ? e.getClass().getSimpleName()
                    : e.getMessage();

            log.warn(
                    "Không gửi được email nhắc cho booking {}: {}",
                    booking.getBookingCode(),
                    message);

            writeLog(runId,booking, email, EmailStatus.FAILED, message);
            return recordOutcome(runId, Outcome.FAILED);
        }

        // Nếu lưu kết quả thành công bị lỗi, để lỗi truyền ra ngoài.
        // Không ghi nhầm lỗi database thành lỗi gửi email.
        writeLog(runId,booking, email, EmailStatus.SENT, null);
       return recordOutcome(runId, Outcome.SENT);
    }

    private void writeLog(
            Long runId,
            Booking booking,
            String email,
            EmailStatus status,
            String error) {
        EmailLog entry = new EmailLog();
        entry.setReminderRunId(runId);
        entry.setBookingId(booking.getId());
        entry.setEmailType(EmailType.REMINDER);
        entry.setRecipientEmail(email);
        entry.setStatus(status);
        entry.setErrorMessage(
                error == null ? null : error.substring(0, Math.min(error.length(), 500)));
        entry.setSentAt(OffsetDateTime.now());

        emailLogRepository.saveAndFlush(entry);
    }
    private Outcome recordOutcome(Long runId, Outcome outcome) {
        reminderRunService.recordOutcome(runId, outcome);
        return outcome;
    }
}