package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ttcs.homestay.dto.RoomCalendarResponse.CellStatus;
import com.ttcs.homestay.entity.*;
import com.ttcs.homestay.repository.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.annotation.Transactional;

class RoomCalendarServiceTest {
    final RoomRepository rooms = mock(RoomRepository.class);
    final BookingRepository bookings = mock(BookingRepository.class);
    final RoomCalendarService service = new RoomCalendarService(rooms, bookings);
    final LocalDate start = LocalDate.of(2027, 6, 10);
    final OffsetDateTime now = OffsetDateTime.parse("2027-06-10T08:00:00+07:00");

    Room room(long id) {
        Room room = new Room();
        room.setId(id); room.setRoomNumber(Long.toString(id));
        room.setRoomType("Double"); room.setStatus(RoomStatus.TRONG_SACH);
        return room;
    }

    Booking booking(Room room, int from, int to, BookingStatus status) {
        Booking booking = new Booking();
        booking.setRoom(room); booking.setStatus(status);
        booking.setCheckInDate(start.plusDays(from)); booking.setCheckOutDate(start.plusDays(to));
        booking.setGuestName("Guest " + from); booking.setBookingCode("BOOK-" + from);
        return booking;
    }

    void given(List<Room> roomList, Booking... assigned) {
        when(rooms.findAllByActiveTrueOrderByRoomNumberAsc()).thenReturn(roomList);
        when(bookings.findOverlappingOnRooms(roomList, start, start.plusDays(3),
                RoomAvailabilityService.OCCUPYING_STATUSES)).thenReturn(Arrays.asList(assigned));
    }

    List<CellStatus> statuses() {
        return service.buildCalendar(start, 3, now).rooms().get(0).cells().stream()
                .map(cell -> cell.status()).toList();
    }

    @ParameterizedTest
    @CsvSource({"0,1,BOOKED,AVAILABLE,AVAILABLE", "0,3,BOOKED,BOOKED,BOOKED",
            "-2,2,BOOKED,BOOKED,AVAILABLE", "-2,0,AVAILABLE,AVAILABLE,AVAILABLE",
            "1,5,AVAILABLE,BOOKED,BOOKED", "3,4,AVAILABLE,AVAILABLE,AVAILABLE"})
    void halfOpenOccupancy(int from, int to, CellStatus first, CellStatus second, CellStatus third) {
        Room room = room(1);
        given(List.of(room), booking(room, from, to, BookingStatus.DA_XAC_NHAN));
        assertThat(statuses()).containsExactly(first, second, third);
    }

    @Test void adjacentBookingsAndUnassignedBookingDoNotOccupyAnotherRoom() {
        Room first = room(1), second = room(2);
        given(List.of(first, second), booking(first, 0, 1, BookingStatus.DA_XAC_NHAN),
                booking(first, 1, 3, BookingStatus.DA_NHAN_PHONG),
                booking(null, 0, 3, BookingStatus.DA_XAC_NHAN));
        var result = service.buildCalendar(start, 3, now);
        assertThat(result.rooms().get(0).cells()).allMatch(cell -> cell.status() == CellStatus.BOOKED);
        assertThat(result.rooms().get(1).cells()).allMatch(cell -> cell.status() == CellStatus.AVAILABLE);
        assertThat(result.rooms()).extracting(row -> row.roomId()).containsExactly(1L, 2L);
    }

    @ParameterizedTest @ValueSource(strings = {"DA_HUY", "DA_HET_HAN", "DA_TRA_PHONG"})
    void inactiveBookingDoesNotOccupy(String status) {
        Room room = room(1);
        given(List.of(room), booking(room, 0, 3, BookingStatus.valueOf(status)));
        assertThat(statuses()).containsOnly(CellStatus.AVAILABLE);
    }

    @ParameterizedTest @CsvSource({"-1,AVAILABLE", "0,AVAILABLE", "1,BOOKED"})
    void holdExpiryBoundaryDoesNotMutateBooking(long seconds, CellStatus expected) {
        Room room = room(1);
        Booking booking = booking(room, 0, 3, BookingStatus.CHO_XAC_NHAN);
        booking.setHoldExpiresAt(now.plusSeconds(seconds));
        given(List.of(room), booking);
        assertThat(statuses()).containsOnly(expected);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CHO_XAC_NHAN);
        assertThat(booking.getHoldExpiresAt()).isEqualTo(now.plusSeconds(seconds));
        assertThat(booking.getRoom()).isSameAs(room);
        verify(rooms).findAllByActiveTrueOrderByRoomNumberAsc();
        verify(bookings).findOverlappingOnRooms(List.of(room), start, start.plusDays(3),
                RoomAvailabilityService.OCCUPYING_STATUSES);
        verifyNoMoreInteractions(rooms, bookings);
    }

    @Test void holdWithoutExpiryRemainsOccupied() {
        Room room = room(1);
        given(List.of(room), booking(room, 0, 3, BookingStatus.CHO_XAC_NHAN));
        assertThat(statuses()).containsOnly(CellStatus.BOOKED);
    }

    @Test void maintenanceIncludesEndDateAndTakesPrecedence() {
        Room room = room(1);
        room.setMaintenanceStartDate(start.plusDays(1)); room.setMaintenanceEndDate(start.plusDays(1));
        given(List.of(room), booking(room, 0, 3, BookingStatus.DA_XAC_NHAN));
        assertThat(statuses()).containsExactly(CellStatus.BOOKED, CellStatus.MAINTENANCE, CellStatus.BOOKED);
    }

    @Test void maintenanceWithoutDatesAndWithoutEndDate() {
        Room room = room(1); room.setStatus(RoomStatus.BAO_TRI);
        given(List.of(room));
        assertThat(statuses()).containsOnly(CellStatus.MAINTENANCE);
        room.setMaintenanceStartDate(start.plusDays(1));
        assertThat(statuses()).containsExactly(CellStatus.AVAILABLE, CellStatus.MAINTENANCE, CellStatus.MAINTENANCE);
    }
    @Test void bookingKeoDaiNhieuDem_moiOCoCungBookingId_oTrongKhongCo() {
        Room room = room(1);
        Booking longStay = booking(room, 0, 2, BookingStatus.DA_XAC_NHAN); longStay.setId(11L);
        given(List.of(room), longStay);
        var cells = service.buildCalendar(start, 3, now).rooms().get(0).cells();
        assertThat(cells).extracting(cell -> cell.bookingId()).containsExactly(11L, 11L, null);
    }

    @Test void haiBookingCungPhong_moiOMoDungBookingCuaNo() {
        Room room = room(1);
        Booking first = booking(room, 0, 1, BookingStatus.DA_XAC_NHAN); first.setId(21L);
        Booking second = booking(room, 1, 3, BookingStatus.DA_NHAN_PHONG); second.setId(22L);
        given(List.of(room), first, second);
        var cells = service.buildCalendar(start, 3, now).rooms().get(0).cells();
        assertThat(cells).extracting(cell -> cell.bookingId()).containsExactly(21L, 22L, 22L);
    }

    @Test void noBookingMeansAvailable() {
        given(List.of(room(1)));
        assertThat(statuses()).containsOnly(CellStatus.AVAILABLE);
    }

    @Test void bookingDetailsFollowTheAssignedBookingForEveryOccupiedNightOnly() {
        Room room = room(1);
        given(List.of(room), booking(room, 0, 2, BookingStatus.DA_XAC_NHAN),
                booking(room, 2, 3, BookingStatus.DA_NHAN_PHONG));
        var cells = service.buildCalendar(start, 3, now).rooms().get(0).cells();
        assertThat(cells).extracting(cell -> cell.guestName()).containsExactly("Guest 0", "Guest 0", "Guest 2");
        assertThat(cells).extracting(cell -> cell.bookingCode()).containsExactly("BOOK-0", "BOOK-0", "BOOK-2");
    }

    @Test void checkoutNightHasNoBookingDetails() {
        Room room = room(1);
        given(List.of(room), booking(room, 0, 1, BookingStatus.DA_XAC_NHAN));
        var cells = service.buildCalendar(start, 3, now).rooms().get(0).cells();
        assertThat(cells.get(1).status()).isEqualTo(CellStatus.AVAILABLE);
        assertThat(cells.get(1).guestName()).isNull();
        assertThat(cells.get(1).bookingCode()).isNull();
    }

    @Test void maintenanceHidesBookingDetailsAndMissingFieldsRemainMissing() {
        Room room = room(1); room.setMaintenanceStartDate(start);
        Booking booking = booking(room, 0, 1, BookingStatus.DA_XAC_NHAN); booking.setGuestName(" "); booking.setBookingCode(null);
        given(List.of(room), booking);
        var cell = service.buildCalendar(start, 3, now).rooms().get(0).cells().get(0);
        assertThat(cell.status()).isEqualTo(CellStatus.MAINTENANCE);
        assertThat(cell.guestName()).isNull(); assertThat(cell.bookingCode()).isNull();
        room.setMaintenanceStartDate(null);
        var bookedCell = service.buildCalendar(start, 3, now).rooms().get(0).cells().get(0);
        assertThat(bookedCell.status()).isEqualTo(CellStatus.BOOKED);
        assertThat(bookedCell.guestName()).isNull(); assertThat(bookedCell.bookingCode()).isNull();
    }

    @Test void overlappingBookingsOnOneNightAreReportedAsInvalidData() {
        Room room = room(1);
        given(List.of(room), booking(room, 0, 2, BookingStatus.DA_XAC_NHAN),
                booking(room, 1, 3, BookingStatus.DA_NHAN_PHONG));
        assertThatThrownBy(() -> service.buildCalendar(start, 3, now))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("multiple bookings");
    }

    @ParameterizedTest @ValueSource(ints = {1, 14})
    void validRange(int days) {
        when(rooms.findAllByActiveTrueOrderByRoomNumberAsc()).thenReturn(List.of());
        var result = service.getCalendar(start, days);
        assertThat(result.dates()).containsExactlyElementsOf(start.datesUntil(start.plusDays(days)).toList());
        assertThat(result.endDateExclusive()).isEqualTo(start.plusDays(days));
        assertThat(result.rooms()).isEmpty();
        verifyNoInteractions(bookings);
    }

    @ParameterizedTest @ValueSource(ints = {0, -1, 15})
    void invalidRange(int days) {
        assertThatThrownBy(() -> service.getCalendar(start, days)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(rooms, bookings);
    }

    @Test void missingDateRejected() {
        assertThatThrownBy(() -> service.getCalendar(null, 14)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(rooms, bookings);
    }

    @Test void transactionIsReadOnly() throws Exception {
        assertThat(RoomCalendarService.class.getMethod("getCalendar", LocalDate.class, int.class)
                .getAnnotation(Transactional.class).readOnly()).isTrue();
    }
}
