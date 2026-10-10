package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.BookingCancelRequest;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

/** S3-05 Lát 2: huỷ booking phải ghi lại người huỷ và thời điểm huỷ, và không đụng booking khác. */
class BookingCancellationAuditTest {

    private static final long ID = 7L;
    private static final String ACTOR = "letan.demo@homestay.local";

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final BookingService service = new BookingService(
            bookingRepository,
            mock(RoomTypeRepository.class),
            mock(OperatingSettingsService.class),
            mock(PricingService.class),
            mock(BookingDepositService.class),
            mock(AuditLogService.class),
            mock(RoomAvailabilityService.class));

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(ACTOR, "n/a", List.of()));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(call -> call.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Booking booking(long id, BookingStatus status) {
        Booking booking = new Booking();
        booking.setBookingCode("BK-AUDIT" + id);
        booking.setGuestName("Khach Test");
        booking.setRoomTypeNameSnapshot("Phong Test");
        booking.setStatus(status);
        booking.setCheckInDate(LocalDate.now().plusDays(10));
        booking.setCheckOutDate(LocalDate.now().plusDays(11));
        booking.setCreatedAt(OffsetDateTime.now());
        when(bookingRepository.findByIdForUpdate(id)).thenReturn(Optional.of(booking));
        return booking;
    }

    private static void assertHttpStatus(Throwable error, int expected) {
        assertThat(error).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(expected);
    }

    @Test
    void huyThanhCongLuuNguoiHuyVaThoiDiemHuy() {
        Booking booking = booking(ID, BookingStatus.DA_XAC_NHAN);
        OffsetDateTime before = OffsetDateTime.now();

        service.cancelBooking(ID, new BookingCancelRequest("Khach doi ke hoach"));

        OffsetDateTime after = OffsetDateTime.now();
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_HUY);
        assertThat(booking.getCancelledBy()).isEqualTo(ACTOR);
        assertThat(booking.getCancelledAt()).isBetween(before, after);
        assertThat(booking.getCancelReason()).isEqualTo("Khach doi ke hoach");
        verify(bookingRepository).save(booking);
    }

    @Test
    void huyBookingChoXacNhanCungGhiNguoiHuy() {
        Booking booking = booking(ID, BookingStatus.CHO_XAC_NHAN);

        service.cancelBooking(ID, new BookingCancelRequest("Trung booking"));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_HUY);
        assertThat(booking.getCancelledBy()).isEqualTo(ACTOR);
        assertThat(booking.getCancelledAt()).isNotNull();
    }

    @Test
    void thieuLyDoThiKhongDoiGiVaKhongGhiDauVet() {
        Booking booking = booking(ID, BookingStatus.DA_XAC_NHAN);

        Throwable error = catchThrowable(
                () -> service.cancelBooking(ID, new BookingCancelRequest("   ")));

        assertHttpStatus(error, 400);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_XAC_NHAN);
        assertThat(booking.getCancelledBy()).isNull();
        assertThat(booking.getCancelledAt()).isNull();
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void huyLanHaiKhongGhiDeNguoiHuyVaThoiDiemHuyCu() {
        Booking booking = booking(ID, BookingStatus.DA_HUY);
        OffsetDateTime firstCancelAt = OffsetDateTime.now().minusDays(1);
        booking.setCancelledBy("nguoi.huy.truoc@homestay.local");
        booking.setCancelledAt(firstCancelAt);

        Throwable error = catchThrowable(
                () -> service.cancelBooking(ID, new BookingCancelRequest("Huy lai")));

        assertHttpStatus(error, 409);
        assertThat(booking.getCancelledBy()).isEqualTo("nguoi.huy.truoc@homestay.local");
        assertThat(booking.getCancelledAt()).isEqualTo(firstCancelAt);
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void khongTimThayBookingThiBao404() {
        when(bookingRepository.findByIdForUpdate(999L)).thenReturn(Optional.empty());

        Throwable error = catchThrowable(
                () -> service.cancelBooking(999L, new BookingCancelRequest("Ly do")));

        assertHttpStatus(error, 404);
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void huyMotBookingKhongDongVaoBookingKhac() {
        Booking cancelled = booking(1L, BookingStatus.DA_XAC_NHAN);
        Booking other = booking(2L, BookingStatus.DA_XAC_NHAN);

        service.cancelBooking(1L, new BookingCancelRequest("Khach doi ke hoach"));

        assertThat(cancelled.getStatus()).isEqualTo(BookingStatus.DA_HUY);
        assertThat(other.getStatus()).isEqualTo(BookingStatus.DA_XAC_NHAN);
        assertThat(other.getCancelledBy()).isNull();
        assertThat(other.getCancelledAt()).isNull();
        verify(bookingRepository, never()).save(other);
    }
}