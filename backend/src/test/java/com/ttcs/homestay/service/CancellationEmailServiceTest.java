package com.ttcs.homestay.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.EmailLog;
import com.ttcs.homestay.entity.EmailStatus;
import com.ttcs.homestay.entity.EmailType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.EmailLogRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;

class CancellationEmailServiceTest {

    private BookingRepository bookingRepository;
    private EmailLogRepository emailLogRepository;
    private MailService mailService;
    private CancellationEmailService service;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        emailLogRepository = mock(EmailLogRepository.class);
        mailService = mock(MailService.class);
        service = new CancellationEmailService(bookingRepository, emailLogRepository, mailService);
    }

    private Booking cancelled(String email, String reason, String note,
            long deposit, int percent, long refund) {
        Booking b = new Booking();
        b.setId(5L);
        b.setBookingCode("BK5");
        b.setGuestName("Nguyễn Văn A");
        b.setGuestEmail(email);
        b.setRoomTypeNameSnapshot("Phòng đôi");
        b.setCheckInDate(LocalDate.of(2026, 10, 20));
        b.setCheckOutDate(LocalDate.of(2026, 10, 22));
        b.setStatus(BookingStatus.DA_HUY);
        b.setCancelReason(reason);
        b.setCancelNote(note);
        b.setCancelDepositAmount(deposit);
        b.setCancelRefundPercent(percent);
        b.setCancelRefundAmount(refund);
        return b;
    }

    @Test
    void sendsEmailAndLogsSent() {
        Booking b = cancelled("khach@example.com", "TRUNG_BOOKING", null, 1_000_000L, 50, 500_000L);
        when(bookingRepository.findByBookingCode("BK5")).thenReturn(Optional.of(b));

        service.sendCancellationEmail("bk5");

        verify(mailService).sendCancellationEmail(b);
        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        assertEquals(EmailStatus.SENT, captor.getValue().getStatus());
        assertEquals(EmailType.CANCELLATION, captor.getValue().getEmailType());
        assertEquals("khach@example.com", captor.getValue().getRecipientEmail());
    }

    @Test
    void missingEmailLogsFailedAndDoesNotThrow() {
        Booking b = cancelled(null, "TRUNG_BOOKING", null, 0L, 0, 0L);
        when(bookingRepository.findByBookingCode("BK5")).thenReturn(Optional.of(b));

        assertDoesNotThrow(() -> service.sendCancellationEmail("BK5"));

        verify(mailService, never()).sendCancellationEmail(any());
        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        assertEquals(EmailStatus.FAILED, captor.getValue().getStatus());
    }

    @Test
    void mailFailureLogsFailedAndDoesNotThrow() {
        Booking b = cancelled("khach@example.com", "TRUNG_BOOKING", null, 0L, 0, 0L);
        when(bookingRepository.findByBookingCode("BK5")).thenReturn(Optional.of(b));
        doThrow(new MailSendException("smtp down")).when(mailService).sendCancellationEmail(b);

        assertDoesNotThrow(() -> service.sendCancellationEmail("BK5"));

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        assertEquals(EmailStatus.FAILED, captor.getValue().getStatus());
    }

    @Test
    void unknownBookingDoesNotThrowAndWritesNothing() {
        when(bookingRepository.findByBookingCode("XX")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.sendCancellationEmail("XX"));

        verify(emailLogRepository, never()).save(any());
    }

    @Test
    void bodyMatchesStoredNumbersFullRefund() {
        Booking b = cancelled("k@e.com", "KHACH_DOI_KE_HOACH", null, 1_500_000L, 100, 1_500_000L);
        String body = MailService.cancellationBody(b);

        assertTrue(MailService.cancellationSubject(b).contains("BK5"));
        assertTrue(body.contains("BK5"));
        assertTrue(body.contains("20/10/2026"));
        assertTrue(body.contains("Khách đổi kế hoạch"));
        assertTrue(body.contains("Tiền cọc đã ghi nhận: 1.500.000 đ"));
        assertTrue(body.contains("Tỷ lệ hoàn cọc áp dụng: 100%"));
        assertTrue(body.contains("Số tiền được hoàn: 1.500.000 đ"));
    }

    @Test
    void bodyShowsNoRefundWhenDepositExistsButRefundIsZero() {
        Booking b = cancelled("k@e.com", "TRUNG_BOOKING", null, 1_000_000L, 0, 0L);
        String body = MailService.cancellationBody(b);

        assertTrue(body.contains("Tỷ lệ hoàn cọc áp dụng: 0%"));
        assertTrue(body.contains("Không hoàn cọc"));
    }

    @Test
    void bodyExplainsZeroWhenNoDeposit() {
        Booking b = cancelled("k@e.com", "TRUNG_BOOKING", null, 0L, 0, 0L);
        String body = MailService.cancellationBody(b);

        assertTrue(body.contains("chưa ghi nhận tiền cọc"));
    }

    @Test
    void reasonLabelsAreShownForEachReason() {
        assertEquals("Khách đổi kế hoạch", MailService.cancelReasonLabel("KHACH_DOI_KE_HOACH"));
        assertEquals("Không liên lạc được với khách", MailService.cancelReasonLabel("KHACH_KHONG_LIEN_LAC"));
        assertEquals("Booking bị trùng", MailService.cancelReasonLabel("TRUNG_BOOKING"));
        assertEquals("Lý do khác", MailService.cancelReasonLabel("LY_DO_KHAC"));
        assertEquals("Lý do viết tay cũ", MailService.cancelReasonLabel("Lý do viết tay cũ"));
    }

    @Test
    void noteIsShownOnlyForOtherReason() {
        Booking other = cancelled("k@e.com", "LY_DO_KHAC", "Chủ nhà có việc đột xuất", 0L, 0, 0L);
        Booking internal = cancelled("k@e.com", "TRUNG_BOOKING", "ghi chú nội bộ", 0L, 0, 0L);

        assertTrue(MailService.cancellationBody(other).contains("Chủ nhà có việc đột xuất"));
        assertFalse(MailService.cancellationBody(internal).contains("ghi chú nội bộ"));
    }

    @Test
    void moneyFormatUsesDotSeparator() {
        assertEquals("1.500.000 đ", MailService.formatMoneyVnd(1_500_000L));
        assertEquals("0 đ", MailService.formatMoneyVnd(0L));
        assertEquals("999 đ", MailService.formatMoneyVnd(999L));
    }
}