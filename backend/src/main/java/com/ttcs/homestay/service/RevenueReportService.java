
package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.report.RevenueReportResponse;
import com.ttcs.homestay.dto.report.RevenueByRoomTypeResponse;
import com.ttcs.homestay.dto.report.RevenueByRoomTypeComparisonResponse;
import com.ttcs.homestay.dto.report.RevenueComparisonResponse;
import com.ttcs.homestay.dto.report.RevenueByDayResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.InvalidRevenueRangeException;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S4-07:
 * Lát 1: Báo cáo tổng doanh thu.
 * Lát 2: Báo cáo doanh thu theo loại phòng.
 * Lát 3: So sánh doanh thu với kỳ liền trước.
 */
@Service
public class RevenueReportService {

    static final Set<BookingStatus> CLOSED_STATUSES =
            EnumSet.of(BookingStatus.DA_TRA_PHONG);

    static final int MAX_RANGE_DAYS = 366;

    private final BookingRepository bookingRepository;
    private final RoomTypeRepository roomTypeRepository;

    public RevenueReportService(
            BookingRepository bookingRepository,
            RoomTypeRepository roomTypeRepository) {
        this.bookingRepository = bookingRepository;
        this.roomTypeRepository = roomTypeRepository;
    }

    /**
     * Lát 1: Báo cáo tổng doanh thu.
     */
    @Transactional(readOnly = true)
    public RevenueReportResponse getReport(
            LocalDate startDate,
            LocalDate endDate) {

        validate(startDate, endDate);

        List<Booking> bookings = bookingRepository
                .findByStatusInAndCheckOutDateBetween(
                        CLOSED_STATUSES, startDate, endDate);

        long totalRevenue = 0;
        long surchargeRevenue = 0;
        long totalNights = 0;

        for (Booking booking : bookings) {
            totalRevenue += booking.getTotalAmount();
            surchargeRevenue += booking.getSurchargeAmount();

            totalNights += ChronoUnit.DAYS.between(
                    booking.getCheckInDate(),
                    booking.getCheckOutDate());
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

    /** Daily revenue is recognized on checkout, matching the existing report range. */
    @Transactional(readOnly = true)
    public List<RevenueByDayResponse> getReportByDay(
            LocalDate startDate, LocalDate endDate, Long roomTypeId) {
        validate(startDate, endDate);

        List<Booking> bookings = bookingRepository
                .findRevenueBookingsByStatusAndCheckOutDateBetweenAndRoomType(
                        CLOSED_STATUSES, startDate, endDate, roomTypeId);
        Map<LocalDate, Long> revenueByDate = new TreeMap<>();
        for (Booking booking : bookings) {
            revenueByDate.merge(booking.getCheckOutDate(), booking.getTotalAmount(), Long::sum);
        }

        List<RevenueByDayResponse> results = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            results.add(new RevenueByDayResponse(date, revenueByDate.getOrDefault(date, 0L)));
        }
        return results;
    }

    /**
     * Lát 3: So sánh doanh thu kỳ hiện tại với kỳ liền trước.
     *
     * Kỳ trước có cùng số ngày với kỳ hiện tại và kết thúc
     * ngay trước ngày bắt đầu của kỳ hiện tại.
     */
    @Transactional(readOnly = true)
    public RevenueComparisonResponse getComparison(
            LocalDate startDate,
            LocalDate endDate) {

        validate(startDate, endDate);

        long rangeDays =
                ChronoUnit.DAYS.between(startDate, endDate) + 1;

        LocalDate previousEnd = startDate.minusDays(1);

        LocalDate previousStart =
                previousEnd.minusDays(rangeDays - 1);

        RevenueReportResponse currentReport =
                getReport(startDate, endDate);

        RevenueReportResponse previousReport =
                getReport(previousStart, previousEnd);

        long currentRevenue = currentReport.totalRevenue();
        long previousRevenue = previousReport.totalRevenue();

        long differenceAmount =
                currentRevenue - previousRevenue;

        BigDecimal changePercent = null;
        String changeDirection;
        String message;

        if (previousRevenue == 0) {
            if (currentRevenue == 0) {
                changeDirection = "UNCHANGED";
                message = "Cả hai kỳ không có doanh thu.";
            } else {
                changeDirection = "NO_BASELINE";
                message =
                        "Có doanh thu kỳ này nhưng kỳ trước bằng 0.";
            }
        } else {
            changePercent = BigDecimal.valueOf(differenceAmount)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(
                            BigDecimal.valueOf(previousRevenue),
                            2,
                            RoundingMode.HALF_UP);

            if (differenceAmount > 0) {
                changeDirection = "INCREASE";
                message = "Doanh thu tăng so với kỳ trước.";
            } else if (differenceAmount < 0) {
                changeDirection = "DECREASE";
                message = "Doanh thu giảm so với kỳ trước.";
            } else {
                changeDirection = "UNCHANGED";
                message = "Doanh thu bằng kỳ trước.";
            }
        }

        return new RevenueComparisonResponse(
                startDate,
                endDate,
                previousStart,
                previousEnd,
                currentRevenue,
                previousRevenue,
                differenceAmount,
                changePercent,
                changeDirection,
                message);
    }
    
    @Transactional(readOnly = true)
    public List<RevenueByRoomTypeComparisonResponse> getComparisonByRoomType(
                    LocalDate startDate,
                    LocalDate endDate,
                    Long roomTypeId) {

            validate(startDate, endDate);

            long rangeDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

            LocalDate previousEnd = startDate.minusDays(1);
            LocalDate previousStart = previousEnd.minusDays(rangeDays - 1);

            List<RevenueByRoomTypeResponse> currentReports = getReportByRoomType(startDate, endDate, roomTypeId);

            List<RevenueByRoomTypeResponse> previousReports = getReportByRoomType(previousStart, previousEnd,
                            roomTypeId);

            Map<Long, RevenueByRoomTypeResponse> previousByRoomType = new HashMap<>();

            for (RevenueByRoomTypeResponse report : previousReports) {
                    previousByRoomType.put(report.roomTypeId(), report);
            }

            List<RevenueByRoomTypeComparisonResponse> results = new ArrayList<>();

            for (RevenueByRoomTypeResponse current : currentReports) {
                    RevenueByRoomTypeResponse previous = previousByRoomType.get(current.roomTypeId());

                    long currentRevenue = current.totalRevenue();
                    long previousRevenue = previous == null ? 0L : previous.totalRevenue();

                    long differenceAmount = currentRevenue - previousRevenue;

                    BigDecimal changePercent = null;
                    String changeDirection;
                    String message;

                    if (previousRevenue == 0) {
                            if (currentRevenue == 0) {
                                    changeDirection = "UNCHANGED";
                                    message = "Cả hai kỳ không có doanh thu.";
                            } else {
                                    changeDirection = "NO_BASELINE";
                                    message = "Có doanh thu kỳ này nhưng kỳ trước bằng 0.";
                            }
                    } else {
                            changePercent = BigDecimal.valueOf(differenceAmount)
                                            .multiply(BigDecimal.valueOf(100))
                                            .divide(
                                                            BigDecimal.valueOf(previousRevenue),
                                                            2,
                                                            RoundingMode.HALF_UP);

                            if (differenceAmount > 0) {
                                    changeDirection = "INCREASE";
                                    message = "Doanh thu tăng so với kỳ trước.";
                            } else if (differenceAmount < 0) {
                                    changeDirection = "DECREASE";
                                    message = "Doanh thu giảm so với kỳ trước.";
                            } else {
                                    changeDirection = "UNCHANGED";
                                    message = "Doanh thu bằng kỳ trước.";
                            }
                    }

                    results.add(new RevenueByRoomTypeComparisonResponse(
                                    current.roomTypeId(),
                                    current.roomTypeCode(),
                                    current.roomTypeName(),
                                    currentRevenue,
                                    previousRevenue,
                                    differenceAmount,
                                    changePercent,
                                    changeDirection,
                                    message));
            }

            return results;
    }

    /**
     * Lát 2: Báo cáo doanh thu theo từng loại phòng.
     *
     * roomTypeId = null: lấy tất cả loại phòng.
     * roomTypeId khác null: chỉ lấy loại phòng được chọn.
     */
    @Transactional(readOnly = true)
    public List<RevenueByRoomTypeResponse> getReportByRoomType(
            LocalDate startDate,
            LocalDate endDate,
            Long roomTypeId) {

        validate(startDate, endDate);

        List<Booking> bookings = bookingRepository
                .findRevenueBookingsByStatusAndCheckOutDateBetween(
                        CLOSED_STATUSES, startDate, endDate);

        Map<Long, List<Booking>> bookingsByRoomType = new HashMap<>();

        for (Booking booking : bookings) {
            RoomType roomType = booking.getRoomType();

            if (roomType != null) {
                bookingsByRoomType
                        .computeIfAbsent(
                                roomType.getId(),
                                id -> new ArrayList<>())
                        .add(booking);
            }
        }

        List<RevenueByRoomTypeResponse> results = new ArrayList<>();

        for (RoomType roomType :
                roomTypeRepository.findAllByOrderByCodeAsc()) {

            if (roomTypeId != null
                    && !roomTypeId.equals(roomType.getId())) {
                continue;
            }

            List<Booking> roomBookings = bookingsByRoomType
                    .getOrDefault(roomType.getId(), List.of());

            long totalRevenue = 0;
            long surchargeRevenue = 0;
            long totalNights = 0;

            for (Booking booking : roomBookings) {
                totalRevenue += booking.getTotalAmount();
                surchargeRevenue += booking.getSurchargeAmount();

                totalNights += ChronoUnit.DAYS.between(
                        booking.getCheckInDate(),
                        booking.getCheckOutDate());
            }

            long roomRevenue = totalRevenue - surchargeRevenue;

            results.add(new RevenueByRoomTypeResponse(
                    roomType.getId(),
                    roomType.getCode(),
                    roomType.getName(),
                    roomBookings.size(),
                    totalNights,
                    roomRevenue,
                    surchargeRevenue,
                    totalRevenue,
                    averageNightlyRate(totalRevenue, totalNights)));
        }

        return results;
    }

    private static Long averageNightlyRate(
            long totalRevenue,
            long totalNights) {

        if (totalNights == 0) {
            return null;
        }

        return BigDecimal.valueOf(totalRevenue)
                .divide(
                        BigDecimal.valueOf(totalNights),
                        0,
                        RoundingMode.HALF_UP)
                .longValue();
    }

    private static void validate(
            LocalDate startDate,
            LocalDate endDate) {

        if (startDate == null || endDate == null) {
            throw new InvalidRevenueRangeException(
                    "Vui lòng chọn ngày bắt đầu và ngày kết thúc.");
        }

        if (endDate.isBefore(startDate)) {
            throw new InvalidRevenueRangeException(
                    "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.");
        }

        if (ChronoUnit.DAYS.between(startDate, endDate) + 1
                > MAX_RANGE_DAYS) {
            throw new InvalidRevenueRangeException(
                    "Khoảng ngày thống kê tối đa "
                            + MAX_RANGE_DAYS + " ngày.");
        }
    }
}
