package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.BookingCancelRequest;
import com.ttcs.homestay.dto.booking.CancelBookingRequest;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.CancelReason;
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

/** S3-05 Lát 3: booking đã nhận phòng (và đã trả phòng) không huỷ được; booking chưa nhận phòng vẫn huỷ bình thường. */
class BookingCancellationBlockTest {

    private static final long ID = 21L;
    private static final String CODE = "BK-BLOCK021";
    private static final String CHECKED_IN_HINT = "trả phòng sớm";
    private static final String NOT_CANCELLABLE_HINT = "Chỉ huỷ được booking đang chờ xác nhận hoặc đã xác nhận";

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final BookingService bookingService = new BookingService(
            bookingRepository,
            mock(RoomTypeRepository.class),
            mock(OperatingSettingsService.class),
            mock(PricingService.class),
            mock(BookingDepositService.class),
            mock(AuditLogService.class),
            mock(RoomAvailabilityService.class));
    private final BookingCancellationService cancellationService =
            new BookingCancellationService(bookingRepository, mock(OperatingSettingsService.class));

    @BeforeEach
    void setUp() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("letan.demo@homestay.local", "n/a", List.of()));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(call -> call.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private Booking booking(BookingStatus status) {
        Booking booking = new Booking();
        booking.setBookingCode(CODE);
        booking.setGuestName("Khach Test");
        booking.setRoomTypeNameSnapshot("Phong Test");
        booking.setStatus(status);
        booking.setCheckInDate(LocalDate.now());
        booking.setCheckOutDate(LocalDate.now().plusDays(1));
        booking.setCreatedAt(OffsetDateTime.now());
        when(bookingRepository.findByIdForUpdate(ID)).thenReturn(Optional.of(booking));
        when(bookingRepository.findByBookingCode(CODE)).thenReturn(Optional.of(booking));
        return booking;
    }

    private static ResponseStatusException conflict(Throwable error) {
        assertThat(error).isInstanceOf(ResponseStatusException.class);
        ResponseStatusException exception = (ResponseStatusException) error;
        assertThat(exception.getStatusCode().value()).isEqualTo(409);
        return exception;
    }

    private void assertUntouched(Booking booking, BookingStatus expectedStatus) {
        assertThat(booking.getStatus()).isEqualTo(expectedStatus);
        assertThat(booking.getCancelReason()).isNull();
        assertThat(booking.getCancelledBy()).isNull();
        assertThat(booking.getCancelledAt()).isNull();
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void huyBangPutBookingDaNhanPhongBiTuChoiVaKhongDoiGi() {
        Booking booking = booking(BookingStatus.DA_NHAN_PHONG);

        Throwable error = catchThrowable(
                () -> bookingService.cancelBooking(ID, new BookingCancelRequest("Khach doi y")));

        assertThat(conflict(error).getReason()).contains(CHECKED_IN_HINT);
        assertUntouched(booking, BookingStatus.DA_NHAN_PHONG);
    }

    @Test
    void huyBangPutBookingDaTraPhongBiTuChoi() {
        Booking booking = booking(BookingStatus.DA_TRA_PHONG);

        Throwable error = catchThrowable(
                () -> bookingService.cancelBooking(ID, new BookingCancelRequest("Khach doi y")));

        assertThat(conflict(error).getReason()).contains(NOT_CANCELLABLE_HINT);
        assertUntouched(booking, BookingStatus.DA_TRA_PHONG);
    }

    @Test
    void xemTruocHuyBookingDaNhanPhongBiTuChoi() {
        Booking booking = booking(BookingStatus.DA_NHAN_PHONG);

        Throwable error = catchThrowable(() -> cancellationService.preview(CODE));

        assertThat(conflict(error).getReason()).contains(CHECKED_IN_HINT);
        assertUntouched(booking, BookingStatus.DA_NHAN_PHONG);
    }

    @Test
    void xemTruocHuyBookingDaTraPhongBiTuChoi() {
        Booking booking = booking(BookingStatus.DA_TRA_PHONG);

        Throwable error = catchThrowable(() -> cancellationService.preview(CODE));

        conflict(error);
        assertUntouched(booking, BookingStatus.DA_TRA_PHONG);
    }

    @Test
    void xacNhanHuyBangServiceHoanCocBookingDaNhanPhongBiTuChoi() {
        Booking booking = booking(BookingStatus.DA_NHAN_PHONG);

        Throwable error = catchThrowable(() -> cancellationService.cancel(
                CODE, new CancelBookingRequest(CancelReason.TRUNG_BOOKING, null)));

        assertThat(conflict(error).getReason()).contains(CHECKED_IN_HINT);
        assertUntouched(booking, BookingStatus.DA_NHAN_PHONG);
    }

    @Test
    void bookingChuaNhanPhongVanHuyBinhThuong() {
        Booking booking = booking(BookingStatus.DA_XAC_NHAN);

        bookingService.cancelBooking(ID, new BookingCancelRequest("Khach doi ke hoach"));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_HUY);
        assertThat(booking.getCancelledBy()).isEqualTo("letan.demo@homestay.local");
        assertThat(booking.getCancelledAt()).isNotNull();
    }
}