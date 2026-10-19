package com.ttcs.homestay.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** S4-04 AC2: nội dung email booking có số đêm, giờ nhận/trả theo tham số và liên kết tra cứu đúng trang. */
class MailServiceBookingEmailTest {

    private Booking booking() {
        Booking b = new Booking();
        b.setBookingCode("M88BAFFU");
        b.setGuestName("Ngô Văn A");
        b.setGuestEmail("khach.test@example.com");
        b.setRoomTypeNameSnapshot("Phòng đôi");
        b.setCheckInDate(LocalDate.of(2026, 10, 21));
        b.setCheckOutDate(LocalDate.of(2026, 10, 24));
        b.setGuestCount(2);
        b.setTotalAmount(1500000L);
        b.setCreatedAt(OffsetDateTime.parse("2026-10-19T10:00:00+07:00"));
        return b;
    }

    @Test
    void createdEmailHasNightsDefaultTimesAndLookupLink() {
        String body = MailService.bookingCreatedBody(booking(), "http://localhost:8088/",
                LocalTime.of(14, 0), LocalTime.of(12, 0));

        assertTrue(body.contains("Số đêm: 3"));
        assertTrue(body.contains("(từ 14:00)"));
        assertTrue(body.contains("(trước 12:00)"));
        assertTrue(body.contains("http://localhost:8088/tra-cuu-booking?ma=M88BAFFU"));
        assertFalse(body.contains("khach.test@example.com"), "Không đưa email khách vào liên kết");
    }

    @Test
    void confirmationEmailUsesOperatingSettingsAtBookingTime() {
        JavaMailSender sender = mock(JavaMailSender.class);
        OperatingSettingsRepository settingsRepository = mock(OperatingSettingsRepository.class);
        OperatingSettings settings = new OperatingSettings();
        settings.setCheckInTime(LocalTime.of(15, 0));
        settings.setCheckOutTime(LocalTime.of(11, 0));
        when(settingsRepository.findFirstByCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(any()))
                .thenReturn(Optional.of(settings));

        MailService mailService = new MailService(sender, "HomeStay <no-reply@homestay.local>",
                "http://localhost:8088", "", "", "", false);
        mailService.setOperatingSettingsRepository(settingsRepository);
        mailService.sendBookingConfirmation(booking(), 500000L);

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        String body = captor.getValue().getText();
        assertTrue(body.contains("(từ 15:00)"));
        assertTrue(body.contains("(trước 11:00)"));
        assertTrue(body.contains("Số đêm: 3"));
        assertTrue(body.contains("/tra-cuu-booking?ma=M88BAFFU"));
    }
}
