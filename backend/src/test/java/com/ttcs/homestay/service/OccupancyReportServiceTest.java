package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

class OccupancyReportServiceTest {
    private final RoomRepository rooms = org.mockito.Mockito.mock(RoomRepository.class);
    private final RoomTypeRepository roomTypes = org.mockito.Mockito.mock(RoomTypeRepository.class);
    private final BookingRepository bookings = org.mockito.Mockito.mock(BookingRepository.class);
    private final OccupancyReportService service = new OccupancyReportService(rooms, roomTypes, bookings);
    private final LocalDate start = LocalDate.of(2027, 6, 10);

    @Test void calculatesDailyAndWeightedAverageAcrossRange() {
        RoomType type = roomType(1, "Standard", true);
        Room normal = room(1, null, null);
        Room maintained = room(2, start.plusDays(1), start.plusDays(1));
        normal.setRoomType(type.getName());
        maintained.setRoomType(type.getName());
        Booking before = booking(-1, 1, BookingStatus.DA_XAC_NHAN);
        Booking after = booking(1, 4, BookingStatus.DA_TRA_PHONG);
        Booking cancelled = booking(0, 3, BookingStatus.DA_HUY);
        before.setRoomType(type);
        after.setRoomType(type);
        cancelled.setRoomType(type);
        when(roomTypes.findAllByOrderByCodeAsc()).thenReturn(List.of(type));
        when(rooms.findAllByActiveTrueOrderByRoomNumberAsc()).thenReturn(List.of(normal, maintained));
        when(bookings.findSoldInDateRange(org.mockito.ArgumentMatchers.eq(start),
                org.mockito.ArgumentMatchers.eq(start.plusDays(3)), org.mockito.ArgumentMatchers.anySet()))
                .thenReturn(List.of(before, after));

        var report = service.report(start, start.plusDays(2));

        assertThat(report.totalSoldNights()).isEqualTo(3);
        assertThat(report.totalAvailableNights()).isEqualTo(5);
        assertThat(report.totalAverageOccupancyPercent()).isEqualByComparingTo("60.00");
        assertThat(report.dailyOccupancies()).extracting(day -> day.soldNights()).containsExactly(1L, 1L, 1L);
        assertThat(report.dailyOccupancies()).extracting(day -> day.availableNights()).containsExactly(2L, 1L, 2L);
    }

    @Test void handlesNoAvailableRoomsWithoutShowingZeroPercent() {
        when(rooms.findAllByActiveTrueOrderByRoomNumberAsc()).thenReturn(List.of(room(1, null, null)));
        when(bookings.findSoldInDateRange(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anySet()))
                .thenReturn(List.of());
        Room unavailable = room(2, start, start);
        when(rooms.findAllByActiveTrueOrderByRoomNumberAsc()).thenReturn(List.of(unavailable));

        var report = service.report(start, start);

        assertThat(report.totalAverageOccupancyPercent()).isNull();
        assertThat(report.dailyOccupancies().get(0).occupancyPercent()).isNull();
    }

    @Test void permits366DaysButRejects367DaysAndReverseDates() {
        when(rooms.findAllByActiveTrueOrderByRoomNumberAsc()).thenReturn(List.of());
        when(bookings.findSoldInDateRange(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anySet()))
                .thenReturn(List.of());
        assertThat(service.report(start, start.plusDays(365)).dailyOccupancies()).hasSize(366);
        assertThatThrownBy(() -> service.report(start, start.plusDays(366)))
                .hasMessageContaining("366 ngày");
        assertThatThrownBy(() -> service.report(start, start.minusDays(1)))
                .hasMessageContaining("Ngày kết thúc");
    }

    private Room room(long id, LocalDate maintenanceStart, LocalDate maintenanceEnd) {
        Room room = new Room();
        room.setId(id); room.setRoomNumber(Long.toString(id)); room.setActive(true);
        room.setStatus(maintenanceStart == null ? RoomStatus.TRONG_SACH : RoomStatus.BAO_TRI);
        room.setMaintenanceStartDate(maintenanceStart); room.setMaintenanceEndDate(maintenanceEnd);
        return room;
    }

    private RoomType roomType(long id, String name, boolean active) {
        RoomType type = new RoomType();
        type.setId(id); type.setCode(name.toUpperCase()); type.setName(name); type.setStatus(active);
        return type;
    }

    private Booking booking(int checkInOffset, int checkOutOffset, BookingStatus status) {
        Booking booking = new Booking();
        booking.setCheckInDate(start.plusDays(checkInOffset));
        booking.setCheckOutDate(start.plusDays(checkOutOffset));
        booking.setStatus(status);
        return booking;
    }
}
