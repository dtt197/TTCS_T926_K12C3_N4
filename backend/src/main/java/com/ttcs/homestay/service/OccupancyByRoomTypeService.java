package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.OccupancyByRoomTypeResponse;
import com.ttcs.homestay.dto.OccupancyByRoomTypeResponse.DailyOccupancy;
import com.ttcs.homestay.dto.OccupancyByRoomTypeResponse.RoomTypeOccupancy;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OccupancyByRoomTypeService {
    private static final Set<BookingStatus> SOLD_STATUSES = Set.of(
            BookingStatus.DA_XAC_NHAN, BookingStatus.DA_NHAN_PHONG, BookingStatus.DA_TRA_PHONG);
    private final RoomRepository rooms;
    private final RoomTypeRepository roomTypes;
    private final BookingRepository bookings;

    public OccupancyByRoomTypeService(RoomRepository rooms, RoomTypeRepository roomTypes, BookingRepository bookings) {
        this.rooms = rooms; this.roomTypes = roomTypes; this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public OccupancyByRoomTypeResponse report(LocalDate startDate, LocalDate endDate, Long selectedRoomTypeId) {
        validateDates(startDate, endDate);
        LocalDate endExclusive = endDate.plusDays(1);
        List<Room> activeRooms = rooms.findAllByActiveTrueOrderByRoomNumberAsc();
        List<Booking> soldBookings = bookings.findSoldInDateRange(startDate, endExclusive, SOLD_STATUSES);
        List<RoomType> allTypes = roomTypes.findAllByOrderByCodeAsc();
        Map<Long, String> typeNames = allTypes.stream()
                .collect(Collectors.toMap(type -> type.getId(), type -> type.getName()));
        List<RoomType> visibleTypes = allTypes.stream()
                .filter(type -> Boolean.TRUE.equals(type.getStatus()) || hasBooking(type, soldBookings))
                .toList();
        if (selectedRoomTypeId != null && visibleTypes.stream().noneMatch(type -> type.getId().equals(selectedRoomTypeId))) {
            throw new IllegalArgumentException("Loại phòng không tồn tại hoặc không có dữ liệu trong kỳ báo cáo.");
        }
        List<RoomTypeOccupancy> summaries = visibleTypes.stream()
                .map(type -> summary(type, startDate, endExclusive, activeRooms, soldBookings, typeNames))
                .sorted(Comparator.comparing((RoomTypeOccupancy item) -> item.code(),
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
        List<DailyOccupancy> daily = startDate.datesUntil(endExclusive)
                .map(day -> daily(day, selectedRoomTypeId, activeRooms, soldBookings, typeNames)).toList();
        long totalSold = summaries.stream().mapToLong(item -> item.soldNights()).sum();
        long totalAvailable = summaries.stream().mapToLong(item -> item.availableNights()).sum();
        return new OccupancyByRoomTypeResponse(startDate, endDate, selectedRoomTypeId,
                totalSold, totalAvailable, percent(totalSold, totalAvailable), summaries, daily);
    }

    private RoomTypeOccupancy summary(RoomType type, LocalDate start, LocalDate end,
            List<Room> rooms, List<Booking> bookings, Map<Long, String> typeNames) {
        long sold = 0, available = 0;
        for (LocalDate day = start; day.isBefore(end); day = day.plusDays(1)) {
            DailyOccupancy result = daily(day, type.getId(), rooms, bookings, typeNames);
            sold += result.soldNights(); available += result.availableNights();
        }
        return new RoomTypeOccupancy(type.getId(), type.getCode(), type.getName(), Boolean.TRUE.equals(type.getStatus()),
                sold, available, percent(sold, available));
    }

    private DailyOccupancy daily(LocalDate day, Long typeId, List<Room> activeRooms, List<Booking> bookings,
            Map<Long, String> typeNames) {
        long available = activeRooms.stream()
                .filter(room -> typeId == null || sameType(room, typeNames.get(typeId)))
                .filter(room -> !RoomAvailabilityService.isUnderMaintenance(room, day)).count();
        long sold = bookings.stream()
                .filter(booking -> typeId == null || (booking.getRoomType() != null && typeId.equals(booking.getRoomType().getId())))
                .filter(booking -> !day.isBefore(booking.getCheckInDate()) && day.isBefore(booking.getCheckOutDate())).count();
        return new DailyOccupancy(day, sold, available, percent(sold, available));
    }

    private static boolean sameType(Room room, String typeName) {
        return typeName != null && room.getRoomType() != null && typeName.equalsIgnoreCase(room.getRoomType());
    }

    private static boolean hasBooking(RoomType type, List<Booking> bookings) {
        return bookings.stream().anyMatch(booking -> booking.getRoomType() != null && type.getId().equals(booking.getRoomType().getId()));
    }

    private static void validateDates(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) throw new IllegalArgumentException("Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.");
        if (start.plusDays(365).isBefore(end)) throw new IllegalArgumentException("Khoảng ngày báo cáo không được vượt quá 366 ngày.");
    }

    private static BigDecimal percent(long sold, long available) {
        return available == 0 ? null : BigDecimal.valueOf(sold).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(available), 2, RoundingMode.HALF_UP);
    }
}
