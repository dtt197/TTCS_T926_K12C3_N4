package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.BookingLookupResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S2-08 Lát 3: tra cứu kèm giờ nhận/trả phòng theo tham số có hiệu lực lúc tạo booking. */
class BookingLookupStayTimesTest {

    private static final OffsetDateTime CREATED_AT = OffsetDateTime.parse("2026-10-05T09:00:00+07:00");

    private BookingRepository bookingRepository;
    private OperatingSettingsRepository settingsRepository;
    private BookingLookupService service;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        settingsRepository = mock(OperatingSettingsRepository.class);
        service = new BookingLookupService(bookingRepository, new BookingLookupRateLimiter(), settingsRepository);

        Booking booking = new Booking();
        booking.setBookingCode("ABCD2345");
        booking.setGuestEmail("khach@example.com");
        booking.setStatus(BookingStatus.DA_XAC_NHAN);
        booking.setRoomTypeNameSnapshot("Phòng đôi");
        booking.setCheckInDate(LocalDate.of(2027, 6, 10));
        booking.setCheckOutDate(LocalDate.of(2027, 6, 13));
        booking.setTotalAmount(1_500_000L);
        booking.setCreatedAt(CREATED_AT);
        when(bookingRepository.findByBookingCode("ABCD2345")).thenReturn(Optional.of(booking));
    }

    private static OperatingSettings settings(LocalTime checkIn, LocalTime checkOut) {
        OperatingSettings settings = new OperatingSettings();
        settings.setCheckInTime(checkIn);
        settings.setCheckOutTime(checkOut);
        return settings;
    }

    @Test
    void dungThamSoCoHieuLucLucTaoBooking() {
        when(settingsRepository.findFirstByCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(CREATED_AT))
                .thenReturn(Optional.of(settings(LocalTime.of(15, 0), LocalTime.of(11, 0))));
        when(settingsRepository.findFirstByOrderByCreatedAtDescIdDesc())
                .thenReturn(Optional.of(settings(LocalTime.of(13, 0), LocalTime.of(10, 0))));

        BookingLookupResponse result = service.lookup("ABCD2345", "khach@example.com");

        assertThat(result.checkInTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(result.checkOutTime()).isEqualTo(LocalTime.of(11, 0));
    }

    @Test
    void bookingCuHonMoiPhienBan_dungThamSoHienTai() {
        when(settingsRepository.findFirstByCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(CREATED_AT))
                .thenReturn(Optional.empty());
        when(settingsRepository.findFirstByOrderByCreatedAtDescIdDesc())
                .thenReturn(Optional.of(settings(LocalTime.of(13, 0), LocalTime.of(10, 0))));

        BookingLookupResponse result = service.lookup("ABCD2345", "khach@example.com");

        assertThat(result.checkInTime()).isEqualTo(LocalTime.of(13, 0));
        assertThat(result.checkOutTime()).isEqualTo(LocalTime.of(10, 0));
    }

    @Test
    void chuaCoThamSo_dungGioMacDinh14hVa12h() {
        when(settingsRepository.findFirstByCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(CREATED_AT))
                .thenReturn(Optional.empty());
        when(settingsRepository.findFirstByOrderByCreatedAtDescIdDesc()).thenReturn(Optional.empty());

        BookingLookupResponse result = service.lookup("ABCD2345", "khach@example.com");

        assertThat(result.checkInTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(result.checkOutTime()).isEqualTo(LocalTime.of(12, 0));
    }
}