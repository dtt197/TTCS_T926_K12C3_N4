package com.ttcs.homestay.service;

import com.ttcs.homestay.entity.EmailLog;
import com.ttcs.homestay.entity.EmailType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.EmailLogRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailLogService {

    public record EmailLogItem(
            Long id,
            Long bookingId,
            String bookingCode,
            String emailType,
            String recipientEmail,
            String status,
            String errorMessage,
            OffsetDateTime sentAt) {
    }

    private final EmailLogRepository emailLogRepository;
    private final BookingRepository bookingRepository;

    public EmailLogService(EmailLogRepository emailLogRepository, BookingRepository bookingRepository) {
        this.emailLogRepository = emailLogRepository;
        this.bookingRepository = bookingRepository;
    }

    /** 200 email gần nhất theo loại, mới nhất ở trên. */
    @Transactional(readOnly = true)
    public List<EmailLogItem> list(EmailType type) {
        List<EmailLog> logs = emailLogRepository.findTop200ByEmailTypeOrderBySentAtDesc(type);
        Set<Long> bookingIds = logs.stream().map(log -> log.getBookingId()).collect(Collectors.toSet());
        Map<Long, String> codes = bookingRepository.findAllById(bookingIds).stream()
                .collect(Collectors.toMap(
                        booking -> Objects.requireNonNull(booking).getId(),
                        booking -> booking.getBookingCode() == null ? "" : booking.getBookingCode()));
        return logs.stream()
                .map(l -> new EmailLogItem(
                        l.getId(),
                        l.getBookingId(),
                        codes.get(l.getBookingId()),
                        l.getEmailType().name(),
                        l.getRecipientEmail(),
                        l.getStatus().name(),
                        l.getErrorMessage(),
                        l.getSentAt()))
                .toList();
    }
}