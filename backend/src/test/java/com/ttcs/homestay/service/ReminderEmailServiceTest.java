package com.ttcs.homestay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

class ReminderEmailServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 14);
    private static final LocalDate TOMORROW = TODAY.plusDays(1);

    private BookingRepository bookingRepository;
    private EmailLogRepository emailLogRepository;
    private MailService mailService;
    private ReminderEmailService service;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        emailLogRepository = mock(EmailLogRepository.class);
        mailService = mock(MailService.class);
        service = new ReminderEmailService(bookingRepository, emailLogRepository, mailService);
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
        verify(emailLogRepository).save(captor.capture());
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
        verify(emailLogRepository, times(2)).save(any(EmailLog.class));
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
}