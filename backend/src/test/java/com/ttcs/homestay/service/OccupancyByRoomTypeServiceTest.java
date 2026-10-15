package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class OccupancyByRoomTypeServiceTest {
    private final RoomRepository rooms = org.mockito.Mockito.mock(RoomRepository.class);
    private final RoomTypeRepository types = org.mockito.Mockito.mock(RoomTypeRepository.class);
    private final BookingRepository bookings = org.mockito.Mockito.mock(BookingRepository.class);
    private final OccupancyByRoomTypeService service = new OccupancyByRoomTypeService(rooms, types, bookings);
    private final LocalDate start = LocalDate.of(2027, 6, 10);

    @Test void totalMatchesSumOfTypesAndSelectedTypeChangesDailyRows() {
        RoomType deluxe = type(1, "DLX", "Deluxe", true);
        RoomType standard = type(2, "STD", "Standard", true);
        when(types.findAllByOrderByCodeAsc()).thenReturn(List.of(deluxe, standard));
        when(rooms.findAllByActiveTrueOrderByRoomNumberAsc()).thenReturn(List.of(
                room("101", "Deluxe", null, null), room("102", "Deluxe", null, null),
                room("201", "Standard", start, start)));
        when(bookings.findSoldInDateRange(any(), any(), anySet())).thenReturn(List.of(
                booking(deluxe, 0, 2), booking(standard, 1, 3)));

        var all = service.report(start, start.plusDays(2), null);
        var deluxeOnly = service.report(start, start.plusDays(2), 1L);

        assertThat(all.totalSoldNights()).isEqualTo(all.roomTypes().stream().mapToLong(t -> t.soldNights()).sum());
        assertThat(all.totalAvailableNights()).isEqualTo(all.roomTypes().stream().mapToLong(t -> t.availableNights()).sum());
        assertThat(deluxeOnly.dailyOccupancies()).extracting(d -> d.availableNights()).containsExactly(2L, 2L, 2L);
        assertThat(deluxeOnly.dailyOccupancies()).extracting(d -> d.soldNights()).containsExactly(1L, 1L, 0L);
        assertThat(all.roomTypes().stream().filter(t -> t.roomTypeId().equals(2L)).findFirst().orElseThrow().averageOccupancyPercent())
                .isEqualByComparingTo("100.00");
    }

    @Test void roomTypeWithAvailableRoomsButNoBookingsShowsZeroPercent() {
        RoomType type = type(1, "STD", "Standard", true);
        when(types.findAllByOrderByCodeAsc()).thenReturn(List.of(type));
        when(rooms.findAllByActiveTrueOrderByRoomNumberAsc()).thenReturn(List.of(room("201", "Standard", null, null)));
        when(bookings.findSoldInDateRange(any(), any(), anySet())).thenReturn(List.of());
        assertThat(service.report(start, start, 1L).roomTypes().get(0).averageOccupancyPercent()).isEqualByComparingTo("0.00");
    }

    private RoomType type(long id, String code, String name, boolean active) {
        RoomType type = new RoomType(); type.setId(id); type.setCode(code); type.setName(name); type.setStatus(active); return type;
    }
    private Room room(String number, String type, LocalDate maintenanceStart, LocalDate maintenanceEnd) {
        Room room = new Room(); room.setRoomNumber(number); room.setRoomType(type); room.setActive(true);
        room.setStatus(maintenanceStart == null ? RoomStatus.TRONG_SACH : RoomStatus.BAO_TRI);
        room.setMaintenanceStartDate(maintenanceStart); room.setMaintenanceEndDate(maintenanceEnd); return room;
    }
    private Booking booking(RoomType type, int from, int to) {
        Booking booking = new Booking(); booking.setRoomType(type); booking.setStatus(BookingStatus.DA_XAC_NHAN);
        booking.setCheckInDate(start.plusDays(from)); booking.setCheckOutDate(start.plusDays(to)); return booking;
    }
}
