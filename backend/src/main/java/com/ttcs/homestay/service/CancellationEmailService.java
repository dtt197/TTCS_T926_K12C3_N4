package com.ttcs.homestay.service;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.EmailLog;
import com.ttcs.homestay.entity.EmailStatus;
import com.ttcs.homestay.entity.EmailType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.EmailLogRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * S4-05 Lát 2: gửi email huỷ sau khi việc huỷ đã được lưu.
 * Mọi lỗi ở đây chỉ ghi log và nhật ký, không bao giờ làm hỏng thao tác huỷ.
 */
@Service
public class CancellationEmailService {

    private static final Logger log = LoggerFactory.getLogger(CancellationEmailService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final BookingRepository bookingRepository;
    private final EmailLogRepository emailLogRepository;
    private final MailService mailService;

    public CancellationEmailService(BookingRepository bookingRepository,
            EmailLogRepository emailLogRepository,
            MailService mailService) {
        this.bookingRepository = bookingRepository;
        this.emailLogRepository = emailLogRepository;
        this.mailService = mailService;
    }

    /** Đọc lại booking đã lưu để số liệu trong email khớp đúng số liệu trên booking. */
    public void sendCancellationEmail(String bookingCode) {
        try {
            String code = bookingCode == null ? "" : bookingCode.trim().toUpperCase(Locale.ROOT);
            Booking booking = bookingRepository.findByBookingCode(code).orElse(null);
            if (booking == null) {
                log.warn("Không tìm thấy booking {} để gửi email huỷ", code);
                return;
            }
            sendFor(booking);
        } catch (Exception e) {
            log.warn("Không gửi được email huỷ cho booking {}: {}", bookingCode, e.getMessage());
        }
    }

    private void sendFor(Booking booking) {
        String email = booking.getGuestEmail();
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            writeLog(booking, email, EmailStatus.FAILED, "Email khách trống hoặc không hợp lệ");
            return;
        }
        try {
            mailService.sendCancellationEmail(booking);
            writeLog(booking, email, EmailStatus.SENT, null);
        } catch (Exception e) {
            log.warn("Không gửi được email huỷ cho booking {}: {}", booking.getBookingCode(), e.getMessage());
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            writeLog(booking, email, EmailStatus.FAILED,
                    message.length() > 500 ? message.substring(0, 500) : message);
        }
    }

    private void writeLog(Booking booking, String email, EmailStatus status, String error) {
        EmailLog entry = new EmailLog();
        entry.setBookingId(booking.getId());
        entry.setEmailType(EmailType.CANCELLATION);
        entry.setRecipientEmail(email);
        entry.setStatus(status);
        entry.setErrorMessage(error);
        entry.setSentAt(OffsetDateTime.now());
        emailLogRepository.save(entry);
    }
}