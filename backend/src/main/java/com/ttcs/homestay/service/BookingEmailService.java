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
 * Xử lý gửi email tiếp nhận đặt phòng và xác nhận booking cho khách hàng,
 * đồng thời ghi nhật ký vào bảng email_logs.
 * Mọi lỗi gửi email chỉ ghi log, không bao giờ làm gián đoạn transaction chính.
 */
@Service
public class BookingEmailService {

    private static final Logger log = LoggerFactory.getLogger(BookingEmailService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final BookingRepository bookingRepository;
    private final EmailLogRepository emailLogRepository;
    private final MailService mailService;

    public BookingEmailService(BookingRepository bookingRepository,
                               EmailLogRepository emailLogRepository,
                               MailService mailService) {
        this.bookingRepository = bookingRepository;
        this.emailLogRepository = emailLogRepository;
        this.mailService = mailService;
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

    private void sendCreatedFor(Booking booking) {
        String email = booking.getGuestEmail();
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            writeLog(booking, email, EmailType.BOOKING_CREATED, EmailStatus.FAILED, "Email khách trống hoặc không hợp lệ");
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
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            writeLog(booking, email, EmailType.CONFIRMATION, EmailStatus.FAILED, "Email khách trống hoặc không hợp lệ");
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
