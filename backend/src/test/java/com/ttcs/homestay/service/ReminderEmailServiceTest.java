package com.ttcs.homestay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.EmailLog;
import com.ttcs.homestay.entity.EmailStatus;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.EmailLogRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import java.util.Optional;
import com.ttcs.homestay.entity.EmailType;

class ReminderEmailServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    private BookingRepository bookingRepository;
    private EmailLogRepository emailLogRepository;
    private MailService mailService;
    private ReminderEmailService service;
    private ReminderRunService reminderRunService;

            @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        emailLogRepository = mock(EmailLogRepository.class);
        mailService = mock(MailService.class);
        reminderRunService = mock(ReminderRunService.class);

        // Mỗi lần kích hoạt được cấp một mã lượt chạy riêng.
        when(reminderRunService.start(TODAY, "MANUAL"))
                .thenReturn(101L, 102L, 103L);

        ReminderBookingProcessor processor = new ReminderBookingProcessor(
                bookingRepository,
                emailLogRepository,
                mailService,
                reminderRunService);

        service = new ReminderEmailService(
                bookingRepository,
                processor,
                reminderRunService);
    }
    private Booking booking(long id, String email) {
        Booking b = new Booking();
        b.setId(id);
        b.setBookingCode("BK" + id);
        b.setGuestName("Nguyễn Văn A");
        b.setGuestEmail(email);
        b.setRoomTypeNameSnapshot("Phòng đôi");
        b.setCheckInDate(TOMORROW);
        b.setCheckOutDate(TOMORROW.plusDays(2));
        b.setStatus(BookingStatus.DA_XAC_NHAN);
        when(bookingRepository.findByIdForUpdate(id))
                  .thenReturn(Optional.of(b));
        return b;
    }

    @Test
    void queriesConfirmedBookingsCheckingInTomorrow() {
        when(bookingRepository.findByStatusAndCheckInDate(BookingStatus.DA_XAC_NHAN, TOMORROW))
                .thenReturn(List.of());

        service.sendReminders(TODAY);

        verify(bookingRepository).findByStatusAndCheckInDate(BookingStatus.DA_XAC_NHAN, TOMORROW);
    }

    @Test
    void sendsEmailAndLogsSent() {
        Booking b = booking(1, "khach@example.com");
        when(bookingRepository.findByStatusAndCheckInDate(BookingStatus.DA_XAC_NHAN, TOMORROW))
                .thenReturn(List.of(b));

        ReminderEmailService.ReminderResult result = service.sendReminders(TODAY);

        assertEquals(1, result.sent());
        assertEquals(0, result.failed());
        verify(mailService).sendCheckInReminder(b);
        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
       verify(emailLogRepository).saveAndFlush(captor.capture());
        assertEquals(EmailStatus.SENT, captor.getValue().getStatus());
        assertEquals("khach@example.com", captor.getValue().getRecipientEmail());
    }

    @Test
    void missingEmailIsSkippedAndOthersStillSent() {
        Booking noEmail = booking(1, null);
        Booking ok = booking(2, "khach@example.com");
        when(bookingRepository.findByStatusAndCheckInDate(BookingStatus.DA_XAC_NHAN, TOMORROW))
                .thenReturn(List.of(noEmail, ok));

        ReminderEmailService.ReminderResult result = service.sendReminders(TODAY);

        assertEquals(2, result.selected());
        assertEquals(1, result.sent());
        assertEquals(1, result.failed());
        verify(mailService, never()).sendCheckInReminder(noEmail);
        verify(mailService).sendCheckInReminder(ok);
        verify(emailLogRepository, times(2)).saveAndFlush(any(EmailLog.class));
    }

    @Test
    void mailFailureDoesNotStopTheRun() {
        Booking first = booking(1, "a@example.com");
        Booking second = booking(2, "b@example.com");
        when(bookingRepository.findByStatusAndCheckInDate(BookingStatus.DA_XAC_NHAN, TOMORROW))
                .thenReturn(List.of(first, second));
        doThrow(new MailSendException("smtp down")).when(mailService).sendCheckInReminder(first);

        ReminderEmailService.ReminderResult result = service.sendReminders(TODAY);

        assertEquals(1, result.sent());
        assertEquals(1, result.failed());
        verify(mailService).sendCheckInReminder(second);
    }

    @Test
    void emailContentHasBookingInfo() {
        Booking b = booking(7, "khach@example.com");

        assertTrue(MailService.reminderSubject(b).contains("15/10/2026"));
        assertTrue(MailService.reminderSubject(b).contains("BK7"));
        String body = MailService.reminderBody(b);
        assertTrue(body.contains("Nguyễn Văn A"));
        assertTrue(body.contains("BK7"));
        assertTrue(body.contains("Phòng đôi"));
        assertTrue(body.contains("15/10/2026"));
    }
    @Test
    void threeRunsSendOnlyOnceAfterSuccessIsRecorded() {
        Booking b = booking(1, "khach@example.com");

        when(bookingRepository.findByStatusAndCheckInDate(
                BookingStatus.DA_XAC_NHAN, TOMORROW))
                .thenReturn(List.of(b));

        // Giả lập lần đầu chưa gửi, các lần sau đã lưu thành công.
        when(emailLogRepository.existsByBookingIdAndEmailTypeAndStatus(
                1L, EmailType.REMINDER, EmailStatus.SENT))
                .thenReturn(false, true, true);

        ReminderEmailService.ReminderResult first = service.sendReminders(TODAY);
        ReminderEmailService.ReminderResult second = service.sendReminders(TODAY);
        ReminderEmailService.ReminderResult third = service.sendReminders(TODAY);

        assertEquals(1, first.sent());
        assertEquals(0, second.sent());
        assertEquals(0, third.sent());
        assertEquals(0, second.failed());
        assertEquals(0, third.failed());

        verify(mailService, times(1)).sendCheckInReminder(b);
        verify(emailLogRepository, times(1))
                .saveAndFlush(any(EmailLog.class));
    }

    @Test
    void failedEmailIsRetriedThenSkippedAfterSuccess() {
        Booking b = booking(1, "khach@example.com");

        when(bookingRepository.findByStatusAndCheckInDate(
                BookingStatus.DA_XAC_NHAN, TOMORROW))
                .thenReturn(List.of(b));

        when(emailLogRepository.existsByBookingIdAndEmailTypeAndStatus(
                1L, EmailType.REMINDER, EmailStatus.SENT))
                .thenReturn(false, false, true);

        when(emailLogRepository.countByBookingIdAndEmailTypeAndStatus(
                1L, EmailType.REMINDER, EmailStatus.FAILED))
                .thenReturn(0L, 1L);

        doThrow(new MailSendException("smtp down"))
                .doNothing()
                .when(mailService).sendCheckInReminder(b);

        ReminderEmailService.ReminderResult first = service.sendReminders(TODAY);
        ReminderEmailService.ReminderResult second = service.sendReminders(TODAY);
        ReminderEmailService.ReminderResult third = service.sendReminders(TODAY);

        assertEquals(1, first.failed());
        assertEquals(0, first.sent());
        assertEquals(1, second.sent());
        assertEquals(0, second.failed());
        assertEquals(0, third.sent());
        assertEquals(0, third.failed());

        verify(mailService, times(2)).sendCheckInReminder(b);

        ArgumentCaptor<EmailLog> captor =
                ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository, times(2))
                .saveAndFlush(captor.capture());

        assertEquals(EmailStatus.FAILED,
                captor.getAllValues().get(0).getStatus());
        assertEquals(EmailStatus.SENT,
                captor.getAllValues().get(1).getStatus());
    }

    @Test
    void threeRecordedFailuresPreventAnotherAttempt() {
        Booking b = booking(1, "khach@example.com");

        when(bookingRepository.findByStatusAndCheckInDate(
                BookingStatus.DA_XAC_NHAN, TOMORROW))
                .thenReturn(List.of(b));

        when(emailLogRepository.countByBookingIdAndEmailTypeAndStatus(
                1L, EmailType.REMINDER, EmailStatus.FAILED))
                .thenReturn(3L);

        ReminderEmailService.ReminderResult result =
                service.sendReminders(TODAY);

        assertEquals(0, result.sent());
        assertEquals(0, result.failed());
        verify(mailService, never()).sendCheckInReminder(any(Booking.class));
        verify(emailLogRepository, never())
                .saveAndFlush(any(EmailLog.class));
    }

    @Test
    void changedCheckInDateIsSkippedAfterReload() {
        Booking b = booking(1, "khach@example.com");

        // Booking có trong danh sách ban đầu.
        when(bookingRepository.findByStatusAndCheckInDate(
                BookingStatus.DA_XAC_NHAN, TOMORROW))
                .thenReturn(List.of(b));

        // Khi processor đọc lại, ngày nhận phòng đã thay đổi.
        b.setCheckInDate(TOMORROW.plusDays(2));

        ReminderEmailService.ReminderResult result =
                service.sendReminders(TODAY);

        assertEquals(0, result.sent());
        assertEquals(0, result.failed());
        verify(mailService, never()).sendCheckInReminder(any(Booking.class));
        verify(emailLogRepository, never())
                .saveAndFlush(any(EmailLog.class));
    }
        @Test
    void cancelledBookingIsSkippedAfterReload() {
        Booking selected = booking(1, "khach@example.com");

        when(bookingRepository.findByStatusAndCheckInDate(
                BookingStatus.DA_XAC_NHAN, TOMORROW))
                .thenReturn(List.of(selected));

        // Danh sách ban đầu đã chọn booking, nhưng khi lấy khoá
        // và đọc lại thì booking đã được huỷ.
        Booking cancelled = new Booking();
        cancelled.setId(1L);
        cancelled.setCheckInDate(TOMORROW);
        cancelled.setStatus(BookingStatus.DA_HUY);

        when(bookingRepository.findByIdForUpdate(1L))
                .thenReturn(Optional.of(cancelled));

        ReminderEmailService.ReminderResult result =
                service.sendReminders(TODAY);

        assertEquals(0, result.sent());
        assertEquals(0, result.failed());

        verify(mailService, never())
                .sendCheckInReminder(any(Booking.class));
        verify(emailLogRepository, never())
                .saveAndFlush(any(EmailLog.class));
    }
        @Test
    void emptyRunStillStartsAndFinishesHistory() {
        when(bookingRepository.findByStatusAndCheckInDate(
                BookingStatus.DA_XAC_NHAN, TOMORROW))
                .thenReturn(List.of());

        ReminderEmailService.ReminderResult result =
                service.sendReminders(TODAY);

        assertEquals(0, result.selected());
        assertEquals(0, result.sent());
        assertEquals(0, result.failed());

        var order = inOrder(reminderRunService);
        order.verify(reminderRunService).start(TODAY, "MANUAL");
        order.verify(reminderRunService).setSelectedCount(101L, 0);
        order.verify(reminderRunService).finish(101L);

        verify(reminderRunService, never())
                .recordOutcome(any(), any());
    }

    @Test
    void unexpectedFailureKeepsEarlierOutcomeAndMarksRunFailed() {
        Booking first = booking(1, "a@example.com");
        Booking second = booking(2, "b@example.com");

        when(bookingRepository.findByStatusAndCheckInDate(
                BookingStatus.DA_XAC_NHAN, TOMORROW))
                .thenReturn(List.of(first, second));

        when(bookingRepository.findByIdForUpdate(2L))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(
                IllegalStateException.class,
                () -> service.sendReminders(TODAY));

        verify(mailService).sendCheckInReminder(first);
        verify(mailService, never()).sendCheckInReminder(second);

        ArgumentCaptor<EmailLog> captor =
                ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).saveAndFlush(captor.capture());

        assertEquals(101L, captor.getValue().getReminderRunId());
        assertEquals(EmailStatus.SENT, captor.getValue().getStatus());

        var order = inOrder(reminderRunService);
        order.verify(reminderRunService).start(TODAY, "MANUAL");
        order.verify(reminderRunService).setSelectedCount(101L, 2);
        order.verify(reminderRunService).recordOutcome(
                101L, ReminderBookingProcessor.Outcome.SENT);
        order.verify(reminderRunService).fail(
                101L, "database unavailable");

        verify(reminderRunService, never()).finish(101L);
    }
}