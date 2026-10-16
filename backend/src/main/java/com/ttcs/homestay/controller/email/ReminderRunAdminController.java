package com.ttcs.homestay.controller.email;

import com.ttcs.homestay.entity.EmailStatus;
import com.ttcs.homestay.entity.EmailType;
import com.ttcs.homestay.entity.ReminderRun;
import com.ttcs.homestay.repository.EmailLogRepository;
import com.ttcs.homestay.repository.ReminderRunRepository;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/reminder-runs")
public class ReminderRunAdminController {

    private final ReminderRunRepository reminderRunRepository;
    private final EmailLogRepository emailLogRepository;

    public ReminderRunAdminController(
            ReminderRunRepository reminderRunRepository,
            EmailLogRepository emailLogRepository) {
        this.reminderRunRepository = reminderRunRepository;
        this.emailLogRepository = emailLogRepository;
    }

    public record RunEmailItem(
            Long id,
            Long bookingId,
            String recipientEmail,
            String status,
            OffsetDateTime sentAt) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Page<ReminderRun> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        return reminderRunRepository.findAllByOrderByStartedAtDescIdDesc(
                pageable(page, size));
    }

    @GetMapping("/{runId}/emails")
    @Transactional(readOnly = true)
    public Page<RunEmailItem> listSentEmails(
            @PathVariable Long runId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageRequest request = pageable(page, size);

        if (!reminderRunRepository.existsById(runId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy lượt chạy");
        }

        return emailLogRepository
                .findByReminderRunIdAndEmailTypeAndStatusOrderBySentAtDescIdDesc(
                        runId,
                        EmailType.REMINDER,
                        EmailStatus.SENT,
                        request)
                .map(entry -> new RunEmailItem(
                        entry.getId(),
                        entry.getBookingId(),
                        entry.getRecipientEmail(),
                        entry.getStatus().name(),
                        entry.getSentAt()));
    }

    private PageRequest pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "page phải >= 0 và size phải từ 1 đến 100");
        }

        return PageRequest.of(page, size);
    }
}