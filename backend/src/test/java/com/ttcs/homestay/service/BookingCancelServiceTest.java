package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.BookingCancelRequest;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.repository.BookingRepository;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** S3-02 Lát 3: lễ tân huỷ booking. */
@ExtendWith(MockitoExtension.class)
class BookingCancelServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private BookingService bookingService;

    private static Booking booking(BookingStatus status) {
        Booking booking = new Booking();
        booking.setId(7L);
        booking.setBookingCode("BK000007");
        booking.setGuestName("Khách thử");
        booking.setStatus(status);
        booking.setRoomTypeNameSnapshot("Phòng đôi");
        booking.setCheckInDate(LocalDate.of(2027, 6, 10));
        booking.setCheckOutDate(LocalDate.of(2027, 6, 12));
        booking.setWeekdayPriceSnapshot(500_000L);
        booking.setWeekendPriceSnapshot(700_000L);
        booking.setWeekendDaysSnapshot("SATURDAY");
        booking.setTotalAmount(1_200_000L);
        return booking;
    }

    private static ResponseStatusException statusError(Throwable error) {
        return (ResponseStatusException) error;
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"CHO_XAC_NHAN", "DA_XAC_NHAN"})
    void huyBookingConHieuLuc_chuyenDaHuyVaLuuLyDoNguoiHuyThoiDiem(BookingStatus status) {
        Booking booking = booking(status);
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        bookingService.cancelBooking(7L, new BookingCancelRequest("  Khách đổi kế hoạch  "));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_HUY);
        assertThat(booking.getCancelReason()).isEqualTo("Khách đổi kế hoạch");
        assertThat(booking.getCancelledBy()).isNotBlank();
        assertThat(booking.getCancelledAt()).isNotNull();
        verify(auditLogService).recordSensitiveAction(
                isNull(), isNull(), isNull(), eq("Booking BK000007"), eq("BOOKING_CANCELLED"), isNull());
    }

    @Test
    void bookingDaNhanPhong_khongHuyDuoc() {
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking(BookingStatus.DA_NHAN_PHONG)));

        assertThatThrownBy(() -> bookingService.cancelBooking(7L, new BookingCancelRequest("Khách đổi ý")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(statusError(error).getStatusCode()).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("trả phòng sớm");
        verify(bookingRepository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = BookingStatus.class, names = {"DA_HUY", "DA_TRA_PHONG", "DA_HET_HAN"})
    void bookingKhongConHieuLuc_khongHuyDuoc(BookingStatus status) {
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking(status)));

        assertThatThrownBy(() -> bookingService.cancelBooking(7L, new BookingCancelRequest("Khách đổi ý")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(statusError(error).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void thieuLyDo_biTuChoiVaKhongDocBooking() {
        assertThatThrownBy(() -> bookingService.cancelBooking(7L, new BookingCancelRequest("   ")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(statusError(error).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(bookingRepository, never()).findByIdForUpdate(anyLong());
    }

    @Test
    void khongTimThayBooking_bao404() {
        when(bookingRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelBooking(99L, new BookingCancelRequest("Khách đổi ý")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(statusError(error).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}