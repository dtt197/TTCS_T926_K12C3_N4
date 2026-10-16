package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.report.RevenueReportResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.exception.InvalidRevenueRangeException;
import com.ttcs.homestay.repository.BookingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S4-07 Lát 1: tổng hợp doanh thu từ các booking đã đóng theo khoảng ngày.
 *
 * Quy tắc tạm dùng cho lát này, chờ PO xác nhận:
 * - Booking "đã đóng" là booking có trạng thái DA_TRA_PHONG.
 * - Booking thuộc khoảng ngày khi ngày trả phòng (checkOutDate) nằm trong [startDate, endDate], gồm cả hai đầu.
 * - Tiền phụ thu là surchargeAmount; tiền phòng là totalAmount trừ surchargeAmount.
 * - Giá bán trung bình mỗi đêm là tổng doanh thu chia tổng số đêm, làm tròn đến đồng.
 */
@Service
public class RevenueReportService {

    /** Các trạng thái được xem là "đã đóng". Đổi ở đây nếu PO chốt quy tắc khác. */
    static final Set<BookingStatus> CLOSED_STATUSES = EnumSet.of(BookingStatus.DA_TRA_PHONG);

    /** Cùng giới hạn với thống kê công suất (S4-06). */
    static final int MAX_RANGE_DAYS = 366;

    private final BookingRepository bookingRepository;

    public RevenueReportService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public RevenueReportResponse getReport(LocalDate startDate, LocalDate endDate) {
        validate(startDate, endDate);

        List<Booking> bookings = bookingRepository
                .findByStatusInAndCheckOutDateBetween(CLOSED_STATUSES, startDate, endDate);

        long totalRevenue = 0;
        long surchargeRevenue = 0;
        long totalNights = 0;
        for (Booking booking : bookings) {
            totalRevenue += booking.getTotalAmount();
            surchargeRevenue += booking.getSurchargeAmount();
            totalNights += ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        }

        return new RevenueReportResponse(
                startDate,
                endDate,
                bookings.size(),
                totalNights,
                totalRevenue - surchargeRevenue,
                surchargeRevenue,
                totalRevenue,
                averageNightlyRate(totalRevenue, totalNights));
    }

    private static Long averageNightlyRate(long totalRevenue, long totalNights) {
        if (totalNights == 0) {
            return null;
        }
        return BigDecimal.valueOf(totalRevenue)
                .divide(BigDecimal.valueOf(totalNights), 0, RoundingMode.HALF_UP)
                .longValue();
    }

    private static void validate(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new InvalidRevenueRangeException("Vui lòng chọn ngày bắt đầu và ngày kết thúc.");
        }
        if (endDate.isBefore(startDate)) {
            throw new InvalidRevenueRangeException("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        }
        if (ChronoUnit.DAYS.between(startDate, endDate) + 1 > MAX_RANGE_DAYS) {
            throw new InvalidRevenueRangeException("Khoảng ngày thống kê tối đa " + MAX_RANGE_DAYS + " ngày.");
        }
    }
}