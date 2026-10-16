package com.ttcs.homestay.service;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.repository.BookingRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ReminderEmailService {

    private static final Logger log =
            LoggerFactory.getLogger(ReminderEmailService.class);

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    public record ReminderResult(int selected, int sent, int failed) {
    }

    private final BookingRepository bookingRepository;
    private final ReminderBookingProcessor reminderBookingProcessor;
    private final ReminderRunService reminderRunService;

    public ReminderEmailService(
            BookingRepository bookingRepository,
            ReminderBookingProcessor reminderBookingProcessor,
            ReminderRunService reminderRunService) {
        this.bookingRepository = bookingRepository;
        this.reminderBookingProcessor = reminderBookingProcessor;
        this.reminderRunService = reminderRunService;
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Ho_Chi_Minh")
    public void runDaily() {
        try {
            ReminderResult result = execute(LocalDate.now(VN), "SCHEDULED");

            log.info(
                    "Nhắc nhận phòng: chọn {}, gửi {}, lỗi {}",
                    result.selected(), result.sent(), result.failed());
        } catch (RuntimeException e) {
            log.error("Tác vụ nhắc nhận phòng kết thúc vì lỗi", e);
        }
    }

    // Giữ phương thức hiện tại cho API kích hoạt thủ công.
    public ReminderResult sendReminders(LocalDate today) {
        return execute(today, "MANUAL");
    }

    private ReminderResult execute(LocalDate today, String triggerType) {
        Objects.requireNonNull(today, "Ngày xử lý không được trống");

        // Bản bắt đầu được commit trước khi chọn booking.
        Long runId = reminderRunService.start(today, triggerType);

        try {
            LocalDate checkInDate = today.plusDays(1);

            List<Long> bookingIds = bookingRepository
                    .findByStatusAndCheckInDate(
                            BookingStatus.DA_XAC_NHAN, checkInDate)
                    .stream()
                    .map(Booking::getId)
                    .toList();

            reminderRunService.setSelectedCount(runId, bookingIds.size());

            int sent = 0;
            int failed = 0;

            for (Long bookingId : bookingIds) {
                ReminderBookingProcessor.Outcome outcome =
                        reminderBookingProcessor.process(
                                bookingId, checkInDate, runId);

                switch (outcome) {
                    case SENT -> sent++;
                    case FAILED -> failed++;
                    case SKIPPED -> {
                        // Processor đã ghi số bỏ qua vào database.
                    }
                }
            }

            // Kể cả không có booking, lượt chạy vẫn được kết thúc.
            reminderRunService.finish(runId);

            return new ReminderResult(bookingIds.size(), sent, failed);
        } catch (RuntimeException e) {
            String message = e.getMessage() == null
                    ? e.getClass().getSimpleName()
                    : e.getMessage();

            try {
                reminderRunService.fail(runId, message);
            } catch (RuntimeException historyError) {
                // Giữ lỗi gốc, đồng thời ghi lại lỗi lưu lịch sử.
                e.addSuppressed(historyError);
                log.error(
                        "Không cập nhật được trạng thái lỗi cho lượt chạy {}",
                        runId,
                        historyError);
            }

            throw e;
        }
    }
}