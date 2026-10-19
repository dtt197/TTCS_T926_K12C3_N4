package com.ttcs.homestay.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingDeposit;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.EmailLog;
import com.ttcs.homestay.entity.EmailStatus;
import com.ttcs.homestay.entity.EmailType;
import com.ttcs.homestay.repository.BookingDepositRepository;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.EmailLogRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S4-04 AC3 (tự gửi lại tối đa 3 lần, cách 5 phút) và AC4 (không có email thì bỏ qua, không ghi lỗi). */
class BookingEmailRetryTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-10-19T10:00:00+07:00");

    private BookingRepository bookingRepository;
    private EmailLogRepository emailLogRepository;
    private BookingDepositRepository depositRepository;
    private MailService mailService;
    private BookingEmailService service;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        emailLogRepository = mock(EmailLogRepository.class);
        depositRepository = mock(BookingDepositRepository.class);
        mailService = mock(MailService.class);
        service = new BookingEmailService(bookingRepository, emailLogRepository, mailService);
        service.setDepositRepository(depositRepository);
    }

    private Booking booking(String email, BookingStatus status) {
        Booking b = new Booking();
        b.setId(10L);
        b.setBookingCode("BK10");
        b.setGuestName("Trần Văn Nam");
        b.setGuestEmail(email);
        b.setRoomTypeNameSnapshot("Phòng đôi");
        b.setCheckInDate(LocalDate.of(2026, 10, 21));
        b.setCheckOutDate(LocalDate.of(2026, 10, 23));
        b.setGuestCount(2);
        b.setTotalAmount(1000000L);
        b.setStatus(status);
        return b;
    }

    private EmailLog failure(EmailType type, OffsetDateTime sentAt) {
        EmailLog entry = new EmailLog();
        entry.setBookingId(10L);
        entry.setEmailType(type);
        entry.setRecipientEmail("nam@example.com");
        entry.setStatus(EmailStatus.FAILED);
        entry.setSentAt(sentAt);
        return entry;
    }

    private void givenFailures(EmailType type, long failedCount, OffsetDateTime lastAttempt) {
        when(emailLogRepository.findByEmailTypeInAndStatusAndSentAtAfter(anyCollection(), eq(EmailStatus.FAILED), any()))
                .thenReturn(List.of(failure(type, lastAttempt)));
        when(emailLogRepository.existsByBookingIdAndEmailTypeAndStatus(10L, type, EmailStatus.SENT)).thenReturn(false);
        when(emailLogRepository.countByBookingIdAndEmailTypeAndStatus(10L, type, EmailStatus.FAILED)).thenReturn(failedCount);
    }

    @Test
    void khongCoEmailThiBoQuaVaKhongGhiLoi() {
        when(bookingRepository.findByBookingCode("BK10")).thenReturn(Optional.of(booking("  ", BookingStatus.CHO_XAC_NHAN)));

        service.sendBookingCreatedEmail("BK10");
        service.sendBookingConfirmedEmail("BK10", 0L);

        verify(mailService, never()).sendBookingCreatedEmail(any());
        verify(mailService, never()).sendBookingConfirmation(any(), any());
        verify(emailLogRepository, never()).save(any());
    }

    @Test
    void guiLaiEmailTiepNhanSau5Phut() {
        Booking b = booking("nam@example.com", BookingStatus.CHO_XAC_NHAN);
        givenFailures(EmailType.BOOKING_CREATED, 1, NOW.minusMinutes(6));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(b));

        assertEquals(1, service.retryFailedEmails(NOW));
        verify(mailService).sendBookingCreatedEmail(b);
        verify(emailLogRepository).save(any(EmailLog.class));
    }

    @Test
    void chuaDu5PhutThiChuaGuiLai() {
        givenFailures(EmailType.BOOKING_CREATED, 1, NOW.minusMinutes(3));

        assertEquals(0, service.retryFailedEmails(NOW));
        verify(mailService, never()).sendBookingCreatedEmail(any());
    }

    @Test
    void daThuLaiDu3LanThiDung() {
        // 1 lần gửi đầu + 3 lần gửi lại đều lỗi = 4 lần lỗi
        givenFailures(EmailType.BOOKING_CREATED, 4, NOW.minusMinutes(6));

        assertEquals(0, service.retryFailedEmails(NOW));
        verify(mailService, never()).sendBookingCreatedEmail(any());
    }

    @Test
    void daGuiThanhCongThiKhongGuiLai() {
        givenFailures(EmailType.BOOKING_CREATED, 1, NOW.minusMinutes(6));
        when(emailLogRepository.existsByBookingIdAndEmailTypeAndStatus(10L, EmailType.BOOKING_CREATED, EmailStatus.SENT))
                .thenReturn(true);

        assertEquals(0, service.retryFailedEmails(NOW));
        verify(bookingRepository, never()).findById(anyLong());
    }

    @Test
    void guiLaiEmailXacNhanVoiTienCocHienTai() {
        Booking b = booking("nam@example.com", BookingStatus.DA_XAC_NHAN);
        BookingDeposit deposit = mock(BookingDeposit.class);
        when(deposit.getAmount()).thenReturn(new BigDecimal("300000"));
        givenFailures(EmailType.CONFIRMATION, 2, NOW.minusMinutes(5));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(b));
        when(depositRepository.findByBooking(b)).thenReturn(Optional.of(deposit));

        assertEquals(1, service.retryFailedEmails(NOW));
        verify(mailService).sendBookingConfirmation(b, 300000L);
    }

    @Test
    void bookingDaHuyThiKhongGuiLaiEmailXacNhan() {
        givenFailures(EmailType.CONFIRMATION, 1, NOW.minusMinutes(6));
        when(bookingRepository.findById(10L)).thenReturn(Optional.of(booking("nam@example.com", BookingStatus.DA_HUY)));

        assertEquals(0, service.retryFailedEmails(NOW));
        verify(mailService, never()).sendBookingConfirmation(any(), any());
    }
}
