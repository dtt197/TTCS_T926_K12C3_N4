package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.RoomCalendarResponse;
import com.ttcs.homestay.dto.RoomCalendarResponse.*;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RoomCalendarService {
    private final RoomRepository rooms;
    private final BookingRepository bookings;

    public RoomCalendarService(RoomRepository rooms, BookingRepository bookings) {
        this.rooms = rooms;
        this.bookings = bookings;
    }

    @Transactional(readOnly = true)
    public RoomCalendarResponse getCalendar(LocalDate startDate, int days) {
        return buildCalendar(startDate, days, OffsetDateTime.now());
    }

    RoomCalendarResponse buildCalendar(LocalDate startDate, int days, OffsetDateTime now) {
        if (startDate == null || days < 1 || days > 14) {
            throw new IllegalArgumentException("startDate is required; days must be between 1 and 14");
        }
        LocalDate end = startDate.plusDays(days);
        List<LocalDate> dates = startDate.datesUntil(end).toList();
        var roomList = rooms.findAllByActiveTrueOrderByRoomNumberAsc();
        if (roomList.isEmpty()) return new RoomCalendarResponse(startDate, end, dates, List.of());
        var byRoom = bookings.findOverlappingOnRooms(roomList, startDate, end,
                        RoomAvailabilityService.OCCUPYING_STATUSES).stream()
                .filter(b -> b.getRoom() != null)
                .filter(b -> RoomAvailabilityService.OCCUPYING_STATUSES.contains(b.getStatus()))
                .collect(Collectors.groupingBy(b -> b.getRoom().getId()));
        var rows = roomList.stream().map(room -> {
            List<Booking> assigned = byRoom.getOrDefault(room.getId(), List.of());
            var cells = dates.stream().map(date -> {
                if (RoomAvailabilityService.isUnderMaintenance(room, date)) {
                    return new Cell(date, CellStatus.MAINTENANCE);
                }
                List<Booking> occupying = assigned.stream()
                        .filter(b -> RoomAvailabilityService.OCCUPYING_STATUSES.contains(b.getStatus()))
                        .filter(b -> RoomAvailabilityService.occupies(b, date, now)).toList();
                if (occupying.size() > 1) {
                    throw new IllegalStateException("Invalid calendar data: multiple bookings occupy room "
                            + room.getId() + " on " + date);
                }
                if (occupying.isEmpty()) return new Cell(date, CellStatus.AVAILABLE);
                Booking booking = occupying.get(0);
                return new Cell(date, CellStatus.BOOKED,
                        hasText(booking.getGuestName()) ? booking.getGuestName() : null,
                        hasText(booking.getBookingCode()) ? booking.getBookingCode() : null);
            }).toList();
            return new Row(room.getId(), room.getRoomNumber(), room.getRoomType(), cells);
        }).toList();
        return new RoomCalendarResponse(startDate, end, dates, rows);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
