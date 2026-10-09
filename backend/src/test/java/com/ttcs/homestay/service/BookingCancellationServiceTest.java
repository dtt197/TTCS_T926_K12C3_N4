package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.BookingCancellationResponse;
import com.ttcs.homestay.dto.booking.CancelBookingRequest;
import com.ttcs.homestay.dto.booking.CancellationPreviewResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.CancelReason;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.repository.BookingRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class BookingCancellationServiceTest {

    private static final String CODE = "BK-TEST0001";

    private final BookingRepository bookingRepository = mock(BookingRepository.class);
    private final OperatingSettingsService settingsService = mock(OperatingSettingsService.class);
    private final BookingCancellationService service =
            new BookingCancellationService(bookingRepository, settingsService);

    @BeforeEach
    void setUp() {
        OperatingSettings settings = new OperatingSettings();
        settings.setCheckInTime(LocalTime.of(14, 0));
        settings.addCancellationTier(72, 100);
        settings.addCancellationTier(24, 50);
        settings.addCancellationTier(6, 20);
        when(settingsService.findEffectiveAt(any())).thenReturn(settings);
        when(bookingRepository.save(any(Booking.class))).thenAnswer(call -> call.getArgument(0));
    }

    private Booking booking(BookingStatus status, int daysFromToday) {
        LocalDate checkIn = LocalDate.now(CancellationRefundCalculator.HOMESTAY_ZONE).plusDays(daysFromToday);
        Booking booking = new Booking();
        booking.setBookingCode(CODE);
        booking.setGuestName("Khach Test");
        booking.setRoomTypeNameSnapshot("Phong Test");
        booking.setStatus(status);
        booking.setCheckInDate(checkIn);
        booking.setCheckOutDate(checkIn.plusDays(1));
        booking.setCreatedAt(OffsetDateTime.now());
        when(bookingRepository.findByBookingCode(CODE)).thenReturn(Optional.of(booking));
        return booking;
    }

    private static CancelBookingRequest request(CancelReason reason, String note) {
        return new CancelBookingRequest(reason, note);
    }

    private static void assertHttpStatus(Throwable error, int expected) {
        assertThat(error).isInstanceOf(ResponseStatusException.class);
        assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(expected);
    }

    @Test
    void xemTruocChoRaTyLeTheoMoc() {
        booking(BookingStatus.DA_XAC_NHAN, 10);
        assertThat(service.preview(CODE).refundPercent()).isEqualTo(100);
        assertThat(service.preview(CODE).appliedTierHours()).isEqualTo(72);

        booking(BookingStatus.DA_XAC_NHAN, 2);
        assertThat(service.preview(CODE).refundPercent()).isEqualTo(50);
        assertThat(service.preview(CODE).appliedTierHours()).isEqualTo(24);

        booking(BookingStatus.DA_XAC_NHAN, -1);
        CancellationPreviewResponse past = service.preview(CODE);
        assertThat(past.refundPercent()).isZero();
        assertThat(past.appliedTierHours()).isNull();
    }

    @Test
    void xemTruocKhongDoiTrangThaiVaKhongLuu() {
        Booking booking = booking(BookingStatus.DA_XAC_NHAN, 10);
        service.preview(CODE);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_XAC_NHAN);
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void huyThanhCongLuuLyDoVaSoTienHeThongTinh() {
        Booking booking = booking(BookingStatus.CHO_XAC_NHAN, 10);
        BookingCancellationResponse result =
                service.cancel(CODE, request(CancelReason.KHACH_DOI_KE_HOACH, null));

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_HUY);
        assertThat(booking.getCancelReason()).isEqualTo("KHACH_DOI_KE_HOACH");
        assertThat(booking.getCancelRefundPercent()).isEqualTo(100);
        assertThat(booking.getCancelRefundAmount()).isZero();
        assertThat(result.status()).isEqualTo(BookingStatus.DA_HUY);
        assertThat(result.refundPercent()).isEqualTo(booking.getCancelRefundPercent());
        assertThat(result.refundAmount()).isEqualTo(booking.getCancelRefundAmount());
        verify(bookingRepository).save(booking);
    }

    @Test
    void khongChonLyDoThiBiTuChoi() {
        Booking booking = booking(BookingStatus.DA_XAC_NHAN, 10);
        Throwable error = catchThrowable(() -> service.cancel(CODE, request(null, null)));

        assertHttpStatus(error, 400);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_XAC_NHAN);
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void lyDoKhacPhaiCoGhiChu() {
        Booking booking = booking(BookingStatus.DA_XAC_NHAN, 10);
        Throwable error = catchThrowable(
                () -> service.cancel(CODE, request(CancelReason.LY_DO_KHAC, "   ")));

        assertHttpStatus(error, 400);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_XAC_NHAN);
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void bookingDaNhanPhongKhongHuyDuoc() {
        Booking booking = booking(BookingStatus.DA_NHAN_PHONG, 0);
        Throwable error = catchThrowable(
                () -> service.cancel(CODE, request(CancelReason.TRUNG_BOOKING, null)));

        assertHttpStatus(error, 409);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.DA_NHAN_PHONG);
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void huyLanHaiBiTuChoi() {
        booking(BookingStatus.DA_HUY, 10);
        Throwable error = catchThrowable(
                () -> service.cancel(CODE, request(CancelReason.TRUNG_BOOKING, null)));

        assertHttpStatus(error, 409);
        verify(bookingRepository, never()).save(any(Booking.class));
    }

    @Test
    void khongTimThayBooking() {
        when(bookingRepository.findByBookingCode("BK-KHONGCO")).thenReturn(Optional.empty());
        Throwable error = catchThrowable(() -> service.preview("bk-khongco"));

        assertHttpStatus(error, 404);
    }
}