package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.OccupancyReportResponse;
import com.ttcs.homestay.dto.OccupancyReportResponse.DailyOccupancy;
import com.ttcs.homestay.dto.OccupancyReportResponse.RoomTypeOccupancy;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OccupancyReportService {
    private static final Set<BookingStatus> SOLD_STATUSES = Set.of(
            BookingStatus.DA_XAC_NHAN, BookingStatus.DA_NHAN_PHONG, BookingStatus.DA_TRA_PHONG);
    private final RoomRepository roomRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final BookingRepository bookingRepository;

    public OccupancyReportService(RoomRepository roomRepository, RoomTypeRepository roomTypeRepository,
                                  BookingRepository bookingRepository) {
        this.roomRepository = roomRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public OccupancyReportResponse report(LocalDate startDate, LocalDate endDate, Long roomTypeId) {
        validateDates(startDate, endDate);
        LocalDate endExclusive = endDate.plusDays(1);
        List<Room> rooms = roomRepository.findAllByActiveTrueOrderByRoomNumberAsc();
        List<Booking> bookings = bookingRepository.findSoldInDateRange(startDate, endExclusive, SOLD_STATUSES);
        List<RoomType> types = roomTypeRepository.findAllByOrderByCodeAsc().stream()
                .filter(type -> Boolean.TRUE.equals(type.getStatus()) || hasBooking(type, bookings)).toList();
        Map<Long, RoomType> typeById = types.stream().collect(Collectors.toMap(RoomType::getId, type -> type));
        List<Room> reportRooms = rooms.stream()
                .filter(room -> types.stream().anyMatch(type -> type.getName().equalsIgnoreCase(room.getRoomType())))
                .toList();
        if (roomTypeId != null && !typeById.containsKey(roomTypeId)) {
            throw new IllegalArgumentException("Loại phòng không tồn tại hoặc không có dữ liệu trong kỳ báo cáo.");
        }
        List<RoomTypeOccupancy> summaries = types.stream()
                .map(type -> summary(type, startDate, endExclusive, reportRooms, bookings)).toList();
        List<DailyOccupancy> days = startDate.datesUntil(endExclusive)
                .map(day -> daily(day, roomTypeId, typeById, reportRooms, bookings)).toList();
        long sold = roomTypeId == null ? summaries.stream().mapToLong(RoomTypeOccupancy::soldNights).sum()
                : days.stream().mapToLong(DailyOccupancy::soldNights).sum();
        long available = roomTypeId == null ? summaries.stream().mapToLong(RoomTypeOccupancy::availableNights).sum()
                : days.stream().mapToLong(DailyOccupancy::availableNights).sum();
        return new OccupancyReportResponse(startDate, endDate, roomTypeId,
                roomTypeId == null ? "Tất cả loại phòng" : typeById.get(roomTypeId).getName(),
                sold, available, percent(sold, available), summaries, days);
    }

    /** Compatibility entry point for callers requesting the all-room report. */
    @Transactional(readOnly = true)
    public OccupancyReportResponse report(LocalDate startDate, LocalDate endDate) {
        return report(startDate, endDate, null);
    }

    private RoomTypeOccupancy summary(RoomType type, LocalDate start, LocalDate end, List<Room> rooms, List<Booking> bookings) {
        long sold = 0, available = 0;
        for (LocalDate day = start; day.isBefore(end); day = day.plusDays(1)) {
            DailyOccupancy daily = daily(day, type.getId(), Map.of(type.getId(), type), rooms, bookings);
            sold += daily.soldNights(); available += daily.availableNights();
        }
        return new RoomTypeOccupancy(type.getId(), type.getCode(), type.getName(), Boolean.TRUE.equals(type.getStatus()),
                sold, available, percent(sold, available));
    }

    private DailyOccupancy daily(LocalDate day, Long selectedTypeId, Map<Long, RoomType> types,
                                 List<Room> rooms, List<Booking> bookings) {
        String selectedName = selectedTypeId == null ? null : types.get(selectedTypeId).getName();
        long available = rooms.stream()
                .filter(room -> selectedName == null || room.getRoomType().equalsIgnoreCase(selectedName))
                .filter(room -> !RoomAvailabilityService.isUnderMaintenance(room, day)).count();
        long sold = bookings.stream()
                .filter(booking -> selectedTypeId == null || (booking.getRoomType() != null && selectedTypeId.equals(booking.getRoomType().getId())))
                .filter(booking -> !day.isBefore(booking.getCheckInDate()) && day.isBefore(booking.getCheckOutDate())).count();
        return new DailyOccupancy(day, sold, available, percent(sold, available));
    }

    private static boolean hasBooking(RoomType type, List<Booking> bookings) {
        return bookings.stream().anyMatch(booking -> booking.getRoomType() != null && type.getId().equals(booking.getRoomType().getId()));
    }

    private static void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) throw new IllegalArgumentException("Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.");
        if (start.plusDays(365).isBefore(end)) throw new IllegalArgumentException("Khoảng ngày báo cáo không được vượt quá 366 ngày.");
    }

    private static BigDecimal percent(long sold, long available) {
        return available == 0 ? null : BigDecimal.valueOf(sold * 100L)
                .divide(BigDecimal.valueOf(available), 2, RoundingMode.HALF_UP);
    }
}
