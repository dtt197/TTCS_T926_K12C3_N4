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
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ReminderEmailService {

    private static final Logger log = LoggerFactory.getLogger(ReminderEmailService.class);
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public record ReminderResult(int selected, int sent, int failed) {
    }

    private final BookingRepository bookingRepository;
    private final EmailLogRepository emailLogRepository;
    private final MailService mailService;

    public ReminderEmailService(BookingRepository bookingRepository,
            EmailLogRepository emailLogRepository,
            MailService mailService) {
        this.bookingRepository = bookingRepository;
        this.emailLogRepository = emailLogRepository;
        this.mailService = mailService;
    }

    /** S4-05: chạy 9:00 mỗi ngày theo giờ Việt Nam, bất kể múi giờ máy chủ. */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Ho_Chi_Minh")
    public void runDaily() {
        ReminderResult result = sendReminders(LocalDate.now(VN));
        log.info("Nhắc nhận phòng: chọn {}, gửi {}, lỗi {}",
                result.selected(), result.sent(), result.failed());
    }

    /** Nhắc các booking đã xác nhận có ngày nhận phòng là (today + 1). */
    public ReminderResult sendReminders(LocalDate today) {
        LocalDate checkInDate = today.plusDays(1);
        List<Booking> bookings = bookingRepository
                .findByStatusAndCheckInDate(BookingStatus.DA_XAC_NHAN, checkInDate);

        int sent = 0;
        int failed = 0;
        for (Booking booking : bookings) {
            if (sendOne(booking)) {
                sent++;
            } else {
                failed++;
            }
        }
        return new ReminderResult(bookings.size(), sent, failed);
    }

    private boolean sendOne(Booking booking) {
        String email = booking.getGuestEmail();
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            writeLog(booking, email, EmailStatus.FAILED, "Email khách trống hoặc không hợp lệ");
            return false;
        }
        try {
            mailService.sendCheckInReminder(booking);
            writeLog(booking, email, EmailStatus.SENT, null);
            return true;
        } catch (Exception e) {
            log.warn("Không gửi được email nhắc cho booking {}: {}", booking.getBookingCode(), e.getMessage());
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            writeLog(booking, email, EmailStatus.FAILED,
                    message.length() > 500 ? message.substring(0, 500) : message);
            return false;
        }
    }

    private void writeLog(Booking booking, String email, EmailStatus status, String error) {
        EmailLog entry = new EmailLog();
        entry.setBookingId(booking.getId());
        entry.setEmailType(EmailType.REMINDER);
        entry.setRecipientEmail(email);
        entry.setStatus(status);
        entry.setErrorMessage(error);
        entry.setSentAt(OffsetDateTime.now());
        emailLogRepository.save(entry);
    }
}