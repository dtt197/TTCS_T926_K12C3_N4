package com.ttcs.homestay.service;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.repository.BookingRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ReminderEmailService {

    private static final Logger log =
            LoggerFactory.getLogger(ReminderEmailService.class);

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    // Giữ nguyên cấu trúc kết quả để không đổi API đang sử dụng.
    public record ReminderResult(int selected, int sent, int failed) {
    }

    private final BookingRepository bookingRepository;
    private final ReminderBookingProcessor reminderBookingProcessor;

    public ReminderEmailService(
            BookingRepository bookingRepository,
            ReminderBookingProcessor reminderBookingProcessor) {
        this.bookingRepository = bookingRepository;
        this.reminderBookingProcessor = reminderBookingProcessor;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Ho_Chi_Minh")
    public void runDaily() {
        ReminderResult result = sendReminders(LocalDate.now(VN));
        log.info(
                "Nhắc nhận phòng: chọn {}, gửi {}, lỗi {}",
                result.selected(), result.sent(), result.failed());
    }

    public ReminderResult sendReminders(LocalDate today) {
        LocalDate checkInDate = today.plusDays(1);

        List<Long> bookingIds = bookingRepository
                .findByStatusAndCheckInDate(
                        BookingStatus.DA_XAC_NHAN, checkInDate)
                .stream()
                .map(Booking::getId)
                .toList();

        int sent = 0;
        int failed = 0;

        for (Long bookingId : bookingIds) {
            ReminderBookingProcessor.Outcome outcome =
                    reminderBookingProcessor.process(bookingId, checkInDate);

            switch (outcome) {
                case SENT -> sent++;
                case FAILED -> failed++;
                case SKIPPED -> {
                    // Bỏ qua, không tính là gửi lỗi.
                }
            }
        }

        return new ReminderResult(bookingIds.size(), sent, failed);
    }
}