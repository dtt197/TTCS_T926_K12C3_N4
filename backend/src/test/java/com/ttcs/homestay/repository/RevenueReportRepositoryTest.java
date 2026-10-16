package com.ttcs.homestay.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ttcs.homestay.dto.report.RevenueReportResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.service.RevenueReportService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * S4-07 Lát 1: báo cáo doanh thu chạy trên CSDL thật (H2), mỗi test tự rollback.
 * Dùng ngày năm 2088 để không lẫn với dữ liệu mẫu.
 */
@SpringBootTest
@Transactional
class RevenueReportRepositoryTest {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RevenueReportService revenueReportService;

    private RoomType doi;

    @BeforeEach
    void setUp() {
        doi = new RoomType();
        doi.setCode("TEST_DOI_REV");
        doi.setName("Phòng đôi (test doanh thu)");
        doi.setStandardCapacity(2);
        doi.setMaxCapacity(3);
        doi.setNumberOfBeds(1);
        doi = roomTypeRepository.save(doi);

        save("REVT0001", BookingStatus.DA_TRA_PHONG, LocalDate.of(2088, 7, 10), LocalDate.of(2088, 7, 12), 1_300_000L, 200_000L);
        save("REVT0002", BookingStatus.DA_TRA_PHONG, LocalDate.of(2088, 7, 12), LocalDate.of(2088, 7, 15), 1_500_000L, 0L);
        save("REVT0003", BookingStatus.DA_NHAN_PHONG, LocalDate.of(2088, 7, 10), LocalDate.of(2088, 7, 12), 900_000L, 0L);
        save("REVT0004", BookingStatus.DA_XAC_NHAN, LocalDate.of(2088, 7, 10), LocalDate.of(2088, 7, 12), 900_000L, 0L);
        save("REVT0005", BookingStatus.DA_HUY, LocalDate.of(2088, 7, 10), LocalDate.of(2088, 7, 12), 900_000L, 0L);
        save("REVT0006", BookingStatus.DA_TRA_PHONG, LocalDate.of(2088, 7, 29), LocalDate.of(2088, 7, 31), 800_000L, 0L);
    }

    private void save(String code, BookingStatus status, LocalDate checkIn, LocalDate checkOut,
            long totalAmount, long surchargeAmount) {
        Booking booking = new Booking();
        booking.setBookingCode(code);
        booking.setGuestName("Khách test");
        booking.setStatus(status);
        booking.setRoomType(doi);
        booking.setRoomTypeNameSnapshot(doi.getName());
        booking.setCheckInDate(checkIn);
        booking.setCheckOutDate(checkOut);
        booking.setWeekdayPriceSnapshot(500_000L);
        booking.setWeekendPriceSnapshot(700_000L);
        booking.setWeekendDaysSnapshot("FRIDAY,SATURDAY");
        booking.setTotalAmount(totalAmount);
        booking.setSurchargeAmount(surchargeAmount);
        booking.setCreatedAt(OffsetDateTime.now());
        bookingRepository.save(booking);
    }

    private RevenueReportResponse report(int fromDay, int toDay) {
        return revenueReportService.getReport(LocalDate.of(2088, 7, fromDay), LocalDate.of(2088, 7, toDay));
    }

    @Test
    void khoangNgayCoBookingDaDong_chiTinhBookingDaTraPhongTrongKhoang() {
        RevenueReportResponse report = report(1, 20);

        assertThat(report.closedBookingCount()).isEqualTo(2);
        assertThat(report.totalNights()).isEqualTo(5);
        assertThat(report.totalRevenue()).isEqualTo(2_800_000L);
        assertThat(report.surchargeRevenue()).isEqualTo(200_000L);
        assertThat(report.roomRevenue()).isEqualTo(2_600_000L);
        assertThat(report.averageNightlyRate()).isEqualTo(560_000L);
    }

    @Test
    void bookingDangOXacNhanVaDaHuyKhongDuocTinh() {
        // Cùng ngày trả phòng 12/07 với booking đã đóng, nhưng chỉ booking đã trả phòng được tính.
        RevenueReportResponse report = report(12, 12);

        assertThat(report.closedBookingCount()).isEqualTo(1);
        assertThat(report.totalRevenue()).isEqualTo(1_300_000L);
    }

    @Test
    void khoangNgayKhongCoBooking_cacChiSoBangKhong() {
        RevenueReportResponse report = report(16, 28);

        assertThat(report.closedBookingCount()).isZero();
        assertThat(report.totalRevenue()).isZero();
        assertThat(report.averageNightlyRate()).isNull();
    }

    @Test
    void haiDauKhoangNgayDeuDuocTinh() {
        // 12/07 là ngày trả phòng của booking A, 15/07 là của booking B.
        assertThat(report(12, 15).closedBookingCount()).isEqualTo(2);
        assertThat(report(13, 15).closedBookingCount()).isEqualTo(1);
        assertThat(report(12, 14).closedBookingCount()).isEqualTo(1);
    }
}