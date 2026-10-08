package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.exception.BookingLookupLimitException;
import com.ttcs.homestay.exception.BookingLookupNotFoundException;
import com.ttcs.homestay.repository.BookingRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** S2-08 Lát 2: tra cứu booking có giới hạn số lần sai theo IP. */
class BookingLookupServiceLimitTest {

    private static final String IP = "203.0.113.10";

    private BookingRepository bookingRepository;
    private BookingLookupRateLimiter rateLimiter;
    private BookingLookupService service;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        rateLimiter = mock(BookingLookupRateLimiter.class);
        service = new BookingLookupService(bookingRepository, rateLimiter, null,
                org.mockito.Mockito.mock(com.ttcs.homestay.repository.BookingDepositRepository.class));

        Booking booking = new Booking();
        booking.setBookingCode("ABCD2345");
        booking.setGuestEmail("khach@example.com");
        booking.setStatus(BookingStatus.DA_XAC_NHAN);
        booking.setRoomTypeNameSnapshot("Phòng đôi");
        booking.setCheckInDate(LocalDate.of(2027, 6, 10));
        booking.setCheckOutDate(LocalDate.of(2027, 6, 13));
        booking.setTotalAmount(1_500_000L);
        when(bookingRepository.findByBookingCode("ABCD2345")).thenReturn(Optional.of(booking));
        when(rateLimiter.blockedUntil(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void traCuuSai_ghiNhanMotLanSaiChoIp() {
        assertThatThrownBy(() -> service.lookup("ABCD2345", "sai@example.com", IP))
                .isInstanceOf(BookingLookupNotFoundException.class);

        verify(rateLimiter).recordFailure(IP);
    }

    @Test
    void traCuuDung_khongBiTinhLaLanSai() {
        assertThat(service.lookup("ABCD2345", "khach@example.com", IP).bookingCode()).isEqualTo("ABCD2345");

        verify(rateLimiter, never()).recordFailure(anyString());
    }

    @Test
    void ipDangBiChan_tuChoiKeCaKhiNhapDung_vaKhongTraCuu() {
        when(rateLimiter.blockedUntil(IP)).thenReturn(Optional.of(Instant.parse("2026-10-05T03:15:00Z")));

        assertThatThrownBy(() -> service.lookup("ABCD2345", "khach@example.com", IP))
                .isInstanceOf(BookingLookupLimitException.class)
                .hasMessage("Bạn đã nhập sai mã booking hoặc email 10 lần trong 15 phút."
                        + " Vui lòng thử lại sau 05/10/2026 10:15.");

        verify(bookingRepository, never()).findByBookingCode(anyString());
        verify(rateLimiter, never()).recordFailure(anyString());
    }

    @Test
    void gioThuLaiLeGiay_lamTronLenPhutKeTiep() {
        when(rateLimiter.blockedUntil(IP)).thenReturn(Optional.of(Instant.parse("2026-10-05T03:14:20Z")));

        assertThatThrownBy(() -> service.lookup("ABCD2345", "khach@example.com", IP))
                .hasMessageEndingWith("Vui lòng thử lại sau 05/10/2026 10:15.");
    }
}
