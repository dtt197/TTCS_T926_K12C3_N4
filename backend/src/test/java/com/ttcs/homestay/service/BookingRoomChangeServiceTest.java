package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingRoomChangeHistory;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.BookingAuditLogRepository;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.BookingRoomChangeHistoryRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class BookingRoomChangeServiceTest {
    @Mock BookingRepository bookings;
    @Mock RoomTypeRepository roomTypes;
    @Mock RoomRepository rooms;
    @Mock RoomAvailabilityService availability;
    @Mock BookingRoomChangeHistoryRepository histories;
    @Mock BookingAuditLogRepository auditLogs;
    @Mock EntityManager entityManager;
    @Mock OperatingSettingsService settings;
    @Mock BookingDepositService deposits;
    @Mock AuditLogService audit;
    private BookingService service;
    private Booking booking;
    private Room oldRoom;
    private Room newRoom;

    @BeforeEach void setUp() {
        service = new BookingService(bookings, roomTypes, settings,
                new PricingService(roomTypes, org.mockito.Mockito.mock(com.ttcs.homestay.repository.PriceOverrideRepository.class), settings),
                deposits, audit, availability, auditLogs, rooms, histories);
        ReflectionTestUtils.setField(service, "entityManager", entityManager);
        RoomType type = new RoomType(); type.setId(1L); type.setName("Double");
        oldRoom = new Room(); oldRoom.setId(10L); oldRoom.setRoomNumber("101"); oldRoom.setRoomType("Double");
        newRoom = new Room(); newRoom.setId(11L); newRoom.setRoomNumber("102"); newRoom.setRoomType("Double");
        booking = new Booking(); booking.setId(4L); booking.setRoomType(type); booking.setRoom(oldRoom);
        booking.setStatus(BookingStatus.DA_XAC_NHAN); booking.setRoomConfirmedAt(OffsetDateTime.now());
        booking.setCheckInDate(LocalDate.now().plusDays(2)); booking.setCheckOutDate(LocalDate.now().plusDays(4));
        booking.setBookingCode("BK-4"); booking.setGuestName("Guest"); booking.setRoomTypeNameSnapshot("Double");
        booking.setCreatedAt(OffsetDateTime.now()); booking.setWeekendDaysSnapshot("SATURDAY,SUNDAY");
        org.mockito.Mockito.lenient().when(bookings.findById(4L)).thenReturn(Optional.of(booking));
        org.mockito.Mockito.lenient().when(bookings.findByIdForUpdate(4L)).thenReturn(Optional.of(booking));
        org.mockito.Mockito.lenient().when(roomTypes.findByIdForUpdate(1L)).thenReturn(Optional.of(booking.getRoomType()));
        org.mockito.Mockito.lenient().when(rooms.findById(11L)).thenReturn(Optional.of(newRoom));
        org.mockito.Mockito.lenient().when(rooms.findByIdForUpdate(11L)).thenReturn(Optional.of(newRoom));
        org.mockito.Mockito.lenient().when(rooms.findByIdForUpdate(10L)).thenReturn(Optional.of(oldRoom));
        org.mockito.Mockito.lenient().when(availability.isRoomAvailable(newRoom, booking.getRoomType(), booking.getCheckInDate(), booking.getCheckOutDate(), 4L)).thenReturn(true);
        org.mockito.Mockito.lenient().when(bookings.save(booking)).thenReturn(booking);
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("7")
                .claim("email", "desk@example.test").claim("fullName", "Receptionist").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        User actor = User.createStaff("Receptionist", "desk@example.test", null, null, true, "hash");
        ReflectionTestUtils.setField(actor, "id", 7L);
        org.mockito.Mockito.lenient().when(entityManager.getReference(User.class, 7L)).thenReturn(actor);
    }

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void changesConfirmedRoomAndPersistsCompleteHistory() {
        service.changeConfirmedRoom(4L, 11L, "  Plumbing repair  ");
        assertThat(booking.getRoom()).isSameAs(newRoom);
        var captor = org.mockito.ArgumentCaptor.forClass(BookingRoomChangeHistory.class);
        verify(histories).save(captor.capture());
        BookingRoomChangeHistory row = captor.getValue();
        assertThat(row.getBooking()).isSameAs(booking);
        assertThat(row.getOldRoom()).isSameAs(oldRoom);
        assertThat(row.getNewRoom()).isSameAs(newRoom);
        assertThat(row.getActor().getId()).isEqualTo(7L);
        assertThat(row.getChangedAt()).isNotNull();
        assertThat(row.getReason()).isEqualTo("Plumbing repair");
        verify(bookings).flush();
        verify(histories).flush();
    }

    @Test void allowsRoomChangeOnCheckInDateUntilBookingIsCheckedIn() {
    booking.setCheckInDate(LocalDate.now());

    when(availability.isRoomAvailable(
            newRoom,
            booking.getRoomType(),
            booking.getCheckInDate(),
            booking.getCheckOutDate(),
            booking.getId()
            )).thenReturn(true);

        service.changeConfirmedRoom(4L, 11L, "Access repair");

        assertThat(booking.getRoom()).isSameAs(newRoom);
        verify(histories).save(any(BookingRoomChangeHistory.class));
    }

    @Test void rejectsRoomChangeForPastCheckInDate() {
        booking.setCheckInDate(LocalDate.now().minusDays(1));
        assertThatThrownBy(() -> service.changeConfirmedRoom(4L, 11L, "Repair"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(booking.getRoom()).isSameAs(oldRoom);
        verify(bookings, never()).save(any());
        verify(histories, never()).save(any());
    }

    @Test void blankReasonIsRejectedWithoutChangingBookingOrHistory() {
        assertThatThrownBy(() -> service.changeConfirmedRoom(4L, 11L, "  "))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(booking.getRoom()).isSameAs(oldRoom);
        verify(bookings, never()).save(any());
        verify(histories, never()).save(any());
    }

    @Test void wrongRoomTypeIsRejectedWithoutChangingBookingOrHistory() {
        newRoom.setRoomType("Suite");
        when(availability.isRoomAvailable(newRoom, booking.getRoomType(), booking.getCheckInDate(), booking.getCheckOutDate(), 4L)).thenReturn(false);
        assertThatThrownBy(() -> service.changeConfirmedRoom(4L, 11L, "Repair"))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(booking.getRoom()).isSameAs(oldRoom);
        verify(histories, never()).save(any());
    }

    @Test void sameRoomAndInvalidBookingStateAreRejected() {
        assertThatThrownBy(() -> service.changeConfirmedRoom(4L, 10L, "Repair"))
                .isInstanceOf(ResponseStatusException.class);
        booking.setStatus(BookingStatus.DA_TRA_PHONG);
        assertThatThrownBy(() -> service.changeConfirmedRoom(4L, 11L, "Repair"))
                .isInstanceOf(ResponseStatusException.class);
        verify(bookings, never()).save(any());
        verify(histories, never()).save(any());
    }
}
