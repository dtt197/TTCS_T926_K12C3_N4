package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.OccupancyReportResponse;
import com.ttcs.homestay.dto.OccupancyReportResponse.DailyOccupancy;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OccupancyReportService {
    /** PO chốt: chỉ các booking đã xác nhận, đang ở, hoặc đã trả phòng là đêm đã bán. */
    static final Set<BookingStatus> SOLD_STATUSES = Set.of(
            BookingStatus.DA_XAC_NHAN, BookingStatus.DA_NHAN_PHONG, BookingStatus.DA_TRA_PHONG);

    private final RoomRepository rooms;
    private final BookingRepository bookings;

    public OccupancyReportService(RoomRepository rooms, BookingRepository bookings) {
        this.rooms = rooms;
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public OccupancyReportResponse report(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.");
        }
        if (startDate.plusDays(365).isBefore(endDate)) {
            throw new IllegalArgumentException("Khoảng ngày báo cáo không được vượt quá 366 ngày.");
        }

        LocalDate endExclusive = endDate.plusDays(1);
        List<Room> activeRooms = rooms.findAllByActiveTrueOrderByRoomNumberAsc();
        List<Booking> soldBookings = bookings.findOccupyingInDateRange(
                startDate, endExclusive, SOLD_STATUSES);
        List<DailyOccupancy> daily = startDate.datesUntil(endExclusive).map(date -> daily(date, activeRooms, soldBookings)).toList();
        long sold = daily.stream().mapToLong(DailyOccupancy::soldNights).sum();
        long available = daily.stream().mapToLong(DailyOccupancy::availableNights).sum();
        return new OccupancyReportResponse(startDate, endDate, sold, available, percent(sold, available), daily);
    }

    private DailyOccupancy daily(LocalDate date, List<Room> activeRooms, List<Booking> soldBookings) {
        long available = activeRooms.stream()
                .filter(room -> !RoomAvailabilityService.isUnderMaintenance(room, date))
                .count();
        // Một booking biểu diễn một phòng/đêm; booking không gán phòng vẫn là một đêm đã bán.
        long sold = soldBookings.stream()
                .filter(booking -> SOLD_STATUSES.contains(booking.getStatus()))
                .filter(booking -> occupiesNight(booking, date))
                .count();
        return new DailyOccupancy(date, sold, available, percent(sold, available));
    }

    private static boolean occupiesNight(Booking booking, LocalDate date) {
        return !date.isBefore(booking.getCheckInDate()) && date.isBefore(booking.getCheckOutDate());
    }

    /** null biểu thị mẫu số bằng 0, để giao diện không hiển thị sai thành 0%. */
    private static BigDecimal percent(long numerator, long denominator) {
        return denominator == 0 ? null : BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }
}
