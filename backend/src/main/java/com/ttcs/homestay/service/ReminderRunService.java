package com.ttcs.homestay.service;

import com.ttcs.homestay.entity.ReminderRun;
import com.ttcs.homestay.repository.ReminderRunRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderRunService {

    private final ReminderRunRepository reminderRunRepository;

    public ReminderRunService(ReminderRunRepository reminderRunRepository) {
        this.reminderRunRepository = reminderRunRepository;
    }

    // Lưu và commit bản bắt đầu trước khi truy vấn booking hoặc gửi email.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long start(LocalDate processingDate, String triggerType) {
        if (processingDate == null) {
            throw new IllegalArgumentException("Ngày xử lý không được trống");
        }

        if (!"SCHEDULED".equals(triggerType)
                && !"MANUAL".equals(triggerType)) {
            throw new IllegalArgumentException("Loại kích hoạt không hợp lệ");
        }

        ReminderRun run = new ReminderRun();
        run.setStartedAt(OffsetDateTime.now(ZoneOffset.UTC));
        run.setProcessingDate(processingDate);
        run.setCheckInDate(processingDate.plusDays(1));
        run.setTriggerType(triggerType);
        run.setStatus("RUNNING");

        return reminderRunRepository.saveAndFlush(run).getId();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void setSelectedCount(Long runId, int selectedCount) {
        if (selectedCount < 0) {
            throw new IllegalArgumentException("Số booking không được âm");
        }

        ReminderRun run = requireRunning(runId);
        run.setSelectedCount(selectedCount);
        reminderRunRepository.saveAndFlush(run);
    }

    // Bắt buộc dùng transaction của processor.
    // Log email và bộ đếm sẽ cùng commit hoặc cùng rollback.
    @Transactional(propagation = Propagation.MANDATORY)
    public void recordOutcome(
            Long runId,
            ReminderBookingProcessor.Outcome outcome) {

        ReminderRun run = requireRunning(runId);

        switch (outcome) {
            case SENT -> run.setSentCount(run.getSentCount() + 1);
            case FAILED -> run.setFailedCount(run.getFailedCount() + 1);
            case SKIPPED -> run.setSkippedCount(run.getSkippedCount() + 1);
        }

        reminderRunRepository.saveAndFlush(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Long runId) {
        ReminderRun run = requireRunning(runId);

        int processed = run.getSentCount()
                + run.getSkippedCount()
                + run.getFailedCount();

        if (processed != run.getSelectedCount()) {
            throw new IllegalStateException(
                    "Số booking đã xử lý không khớp số booking được chọn");
        }

        run.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        run.setStatus(
                run.getFailedCount() > 0
                        ? "COMPLETED_WITH_ERRORS"
                        : "COMPLETED");

        reminderRunRepository.saveAndFlush(run);
    }

    // Giữ nguyên các bộ đếm đã commit trước khi xảy ra lỗi tổng thể.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long runId, String errorMessage) {
        ReminderRun run = requireRunning(runId);

        String message = errorMessage == null
                ? "Lỗi không xác định"
                : errorMessage;

        run.setFinishedAt(OffsetDateTime.now(ZoneOffset.UTC));
        run.setStatus("FAILED");
        run.setErrorMessage(
                message.substring(0, Math.min(message.length(), 1000)));

        reminderRunRepository.saveAndFlush(run);
    }

    private ReminderRun requireRunning(Long runId) {
        ReminderRun run = reminderRunRepository.findByIdForUpdate(runId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Không tìm thấy lượt chạy: " + runId));

        if (!"RUNNING".equals(run.getStatus())) {
            throw new IllegalStateException(
                    "Lượt chạy đã kết thúc: " + runId);
        }

        return run;
    }
}