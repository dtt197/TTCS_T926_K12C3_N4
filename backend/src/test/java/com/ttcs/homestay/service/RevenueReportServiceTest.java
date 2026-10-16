package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.report.RevenueReportResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.exception.InvalidRevenueRangeException;
import com.ttcs.homestay.repository.BookingRepository;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RevenueReportServiceTest {

    private static final LocalDate START = LocalDate.of(2088, 7, 1);
    private static final LocalDate END = LocalDate.of(2088, 7, 31);

    @Mock
    private BookingRepository bookingRepository;

    @InjectMocks
    private RevenueReportService revenueReportService;

    private static Booking closed(int nights, long totalAmount, long surchargeAmount) {
        Booking booking = new Booking();
        booking.setStatus(BookingStatus.DA_TRA_PHONG);
        booking.setCheckInDate(START.plusDays(5));
        booking.setCheckOutDate(START.plusDays(5L + nights));
        booking.setTotalAmount(totalAmount);
        booking.setSurchargeAmount(surchargeAmount);
        return booking;
    }

    @Test
    void tongDoanhThuBangTienPhongCongTienPhuThu() {
        when(bookingRepository.findByStatusInAndCheckOutDateBetween(any(), any(), any()))
                .thenReturn(List.of(closed(2, 1_300_000L, 200_000L), closed(3, 1_500_000L, 0L)));

        RevenueReportResponse report = revenueReportService.getReport(START, END);

        assertThat(report.totalRevenue()).isEqualTo(2_800_000L);
        assertThat(report.surchargeRevenue()).isEqualTo(200_000L);
        assertThat(report.roomRevenue()).isEqualTo(2_600_000L);
        assertThat(report.roomRevenue() + report.surchargeRevenue()).isEqualTo(report.totalRevenue());
    }

    @Test
    void demSoBookingVaTinhGiaBanTrungBinhMoiDem() {
        when(bookingRepository.findByStatusInAndCheckOutDateBetween(any(), any(), any()))
                .thenReturn(List.of(closed(2, 1_300_000L, 200_000L), closed(3, 1_500_000L, 0L)));

        RevenueReportResponse report = revenueReportService.getReport(START, END);

        assertThat(report.closedBookingCount()).isEqualTo(2);
        assertThat(report.totalNights()).isEqualTo(5);
        // 2.800.000 / 5 đêm = 560.000
        assertThat(report.averageNightlyRate()).isEqualTo(560_000L);
    }

    @Test
    void giaTrungBinhLamTronDenDong() {
        when(bookingRepository.findByStatusInAndCheckOutDateBetween(any(), any(), any()))
                .thenReturn(List.of(closed(3, 1_000_000L, 0L)));

        // 1.000.000 / 3 = 333.333,33 -> 333.333
        assertThat(revenueReportService.getReport(START, END).averageNightlyRate()).isEqualTo(333_333L);
    }

    @Test
    void khoangNgayKhongCoBooking_cacChiSoBangKhongVaGiaTrungBinhRong() {
        when(bookingRepository.findByStatusInAndCheckOutDateBetween(any(), any(), any())).thenReturn(List.of());

        RevenueReportResponse report = revenueReportService.getReport(START, END);

        assertThat(report.closedBookingCount()).isZero();
        assertThat(report.totalNights()).isZero();
        assertThat(report.totalRevenue()).isZero();
        assertThat(report.roomRevenue()).isZero();
        assertThat(report.surchargeRevenue()).isZero();
        assertThat(report.averageNightlyRate()).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void chiTruyVanBookingDaDong_bookingChuaDongKhongDuocTinh() {
        when(bookingRepository.findByStatusInAndCheckOutDateBetween(any(), any(), any())).thenReturn(List.of());

        revenueReportService.getReport(START, END);

        ArgumentCaptor<Collection<BookingStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        verify(bookingRepository).findByStatusInAndCheckOutDateBetween(statuses.capture(), any(), any());
        assertThat(statuses.getValue()).containsExactly(BookingStatus.DA_TRA_PHONG);
    }

    @Test
    void ngayKetThucTruocNgayBatDau_biTuChoi() {
        assertThatThrownBy(() -> revenueReportService.getReport(END, START))
                .isInstanceOf(InvalidRevenueRangeException.class)
                .hasMessage("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void khoangNgayQua366Ngay_biTuChoi() {
        assertThatThrownBy(() -> revenueReportService.getReport(START, START.plusDays(366)))
                .isInstanceOf(InvalidRevenueRangeException.class);
        verifyNoInteractions(bookingRepository);
    }

    @Test
    void khoangNgayDung366Ngay_duocChapNhan() {
        when(bookingRepository.findByStatusInAndCheckOutDateBetween(any(), any(), any())).thenReturn(List.of());

        assertThat(revenueReportService.getReport(START, START.plusDays(365)).closedBookingCount()).isZero();
    }

    @Test
    void thieuNgay_biTuChoi() {
        assertThatThrownBy(() -> revenueReportService.getReport(null, END))
                .isInstanceOf(InvalidRevenueRangeException.class);
        assertThatThrownBy(() -> revenueReportService.getReport(START, null))
                .isInstanceOf(InvalidRevenueRangeException.class);
    }
}