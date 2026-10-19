package com.ttcs.homestay.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;

class BookingEmailServiceTest {

    private BookingRepository bookingRepository;
    private EmailLogRepository emailLogRepository;
    private MailService mailService;
    private BookingEmailService service;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        emailLogRepository = mock(EmailLogRepository.class);
        mailService = mock(MailService.class);
        service = new BookingEmailService(bookingRepository, emailLogRepository, mailService);
    }

    private Booking sampleBooking(String email, BookingStatus status) {
        Booking b = new Booking();
        b.setId(10L);
        b.setBookingCode("BK10");
        b.setGuestName("Trần Văn Nam");
        b.setGuestEmail(email);
        b.setRoomTypeNameSnapshot("Phòng Đôi Cao Cấp");
        b.setCheckInDate(LocalDate.of(2026, 10, 20));
        b.setCheckOutDate(LocalDate.of(2026, 10, 22));
        b.setGuestCount(2);
        b.setTotalAmount(1000000L);
        b.setStatus(status);
        b.setHoldExpiresAt(OffsetDateTime.now().plusHours(24));
        return b;
    }

    @Test
    void sendBookingCreatedEmail_success() {
        Booking b = sampleBooking("nam@example.com", BookingStatus.CHO_XAC_NHAN);
        when(bookingRepository.findByBookingCode("BK10")).thenReturn(Optional.of(b));

        service.sendBookingCreatedEmail("BK10");

        verify(mailService).sendBookingCreatedEmail(b);
        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        EmailLog saved = captor.getValue();
        assertEquals(EmailType.BOOKING_CREATED, saved.getEmailType());
        assertEquals(EmailStatus.SENT, saved.getStatus());
        assertEquals("nam@example.com", saved.getRecipientEmail());
        assertNull(saved.getErrorMessage());
    }

    @Test
    void sendBookingCreatedEmail_invalidEmail() {
        Booking b = sampleBooking("khong-hop-le", BookingStatus.CHO_XAC_NHAN);
        when(bookingRepository.findByBookingCode("BK10")).thenReturn(Optional.of(b));

        service.sendBookingCreatedEmail("BK10");

        verify(mailService, never()).sendBookingCreatedEmail(any());
        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        EmailLog saved = captor.getValue();
        assertEquals(EmailType.BOOKING_CREATED, saved.getEmailType());
        assertEquals(EmailStatus.FAILED, saved.getStatus());
    }

    @Test
    void sendBookingConfirmedEmail_success() {
        Booking b = sampleBooking("nam@example.com", BookingStatus.DA_XAC_NHAN);
        when(bookingRepository.findByBookingCode("BK10")).thenReturn(Optional.of(b));

        service.sendBookingConfirmedEmail("BK10", 500000L);

        verify(mailService).sendBookingConfirmation(b, 500000L);
        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        EmailLog saved = captor.getValue();
        assertEquals(EmailType.CONFIRMATION, saved.getEmailType());
        assertEquals(EmailStatus.SENT, saved.getStatus());
        assertEquals("nam@example.com", saved.getRecipientEmail());
    }

    @Test
    void sendBookingConfirmedEmail_smtpError_doesNotThrow() {
        Booking b = sampleBooking("nam@example.com", BookingStatus.DA_XAC_NHAN);
        when(bookingRepository.findByBookingCode("BK10")).thenReturn(Optional.of(b));
        doThrow(new MailSendException("SMTP error")).when(mailService).sendBookingConfirmation(b, 500000L);

        assertDoesNotThrow(() -> service.sendBookingConfirmedEmail("BK10", 500000L));

        ArgumentCaptor<EmailLog> captor = ArgumentCaptor.forClass(EmailLog.class);
        verify(emailLogRepository).save(captor.capture());
        EmailLog saved = captor.getValue();
        assertEquals(EmailType.CONFIRMATION, saved.getEmailType());
        assertEquals(EmailStatus.FAILED, saved.getStatus());
        assertTrue(saved.getErrorMessage().contains("MailSendException") || saved.getErrorMessage().contains("SMTP"));
    }
}
