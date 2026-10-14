package com.ttcs.homestay.controller.email;

import com.ttcs.homestay.entity.EmailType;
import com.ttcs.homestay.service.EmailLogService;
import com.ttcs.homestay.service.ReminderEmailService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** S4-05: chỉ ADMIN (đã chặn bởi /api/admin/** trong SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class EmailAdminController {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final ReminderEmailService reminderEmailService;
    private final EmailLogService emailLogService;

    public EmailAdminController(ReminderEmailService reminderEmailService,
            EmailLogService emailLogService) {
        this.reminderEmailService = reminderEmailService;
        this.emailLogService = emailLogService;
    }

    /** Chạy nhắc ngay. date = "hôm nay" giả lập; nhắc cho booking nhận phòng vào date + 1. */
    @PostMapping("/reminders/run")
    public ReminderEmailService.ReminderResult runReminders(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        LocalDate today = date != null ? date : LocalDate.now(VN);
        return reminderEmailService.sendReminders(today);
    }

    @GetMapping("/email-logs")
    public List<EmailLogService.EmailLogItem> listEmailLogs(
            @RequestParam(defaultValue = "REMINDER") EmailType type) {
        return emailLogService.list(type);
    }
}