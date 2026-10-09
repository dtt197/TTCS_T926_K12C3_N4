package com.ttcs.homestay.controller.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoleRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.security.JwtTokenService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:booking_room_assignment_it;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class BookingRoomAssignmentIntegrationTest {

    private static final LocalDate CHECK_IN = LocalDate.of(2034, 3, 10);
    private static final String CONFLICT_CODE = "BK-ROOM-OWNER-301";

    @Autowired private MockMvc mvc;
    @Autowired private BookingRepository bookings;
    @Autowired private RoomRepository rooms;
    @Autowired private RoomTypeRepository roomTypes;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private JwtTokenService tokens;

    private Room occupiedRoom;
    private Room temporaryRoom;
    private Room secondTemporaryRoom;
    private RoomType roomType;
    private String accessToken;

    @BeforeEach
    void setUp() {
        bookings.deleteAll();
        rooms.deleteAll();
        roomTypes.deleteAll();
        users.deleteAll();
        roles.deleteAll();

        jdbcRoleSeed();
        var receptionistRole = roles.findByCode("RECEPTIONIST").orElseThrow();
        User actor = User.createStaff("Receptionist", "room-assignment-it@homestay.local", null,
                receptionistRole, true, "unused-test-hash");
        actor.changePassword("changed-test-hash");
        actor = users.saveAndFlush(actor);
        accessToken = tokens.issueTokens(actor).accessToken();

        roomType = new RoomType();
        roomType.setCode("ROOM-ASSIGN-IT");
        roomType.setName("Room Assignment IT");
        roomType.setStandardCapacity(2);
        roomType.setMaxCapacity(3);
        roomType.setNumberOfBeds(1);
        roomType = roomTypes.saveAndFlush(roomType);

        occupiedRoom = room("301");
        temporaryRoom = room("302");
        secondTemporaryRoom = room("303");
    }

    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;

    private void jdbcRoleSeed() {
        jdbc.update("INSERT INTO roles (code, name) VALUES (?, ?)", "RECEPTIONIST", "Receptionist");
    }

    @Test
    void overlappingStayReturnsConflictCodeAndLeavesTargetUnchanged() throws Exception {
        Booking owner = booking(CONFLICT_CODE, CHECK_IN, CHECK_IN.plusDays(1), occupiedRoom,
                BookingStatus.CHO_XAC_NHAN);
        Booking target = booking("BK-ROOM-TARGET-301", CHECK_IN, CHECK_IN.plusDays(1), temporaryRoom,
                BookingStatus.DA_XAC_NHAN);

        mvc.perform(put("/api/bookings/{id}/room", target.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":" + occupiedRoom.getId() + "}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(CONFLICT_CODE)));

        Booking unchanged = bookings.findById(target.getId()).orElseThrow();
        assertThat(unchanged.getRoom().getId()).isEqualTo(temporaryRoom.getId());
        assertThat(unchanged.getRoomConfirmedAt()).isNull();
        assertThat(unchanged.getStatus()).isEqualTo(BookingStatus.DA_XAC_NHAN);
        assertThat(bookings.findById(owner.getId()).orElseThrow().getRoom().getId()).isEqualTo(occupiedRoom.getId());
    }

    @Test
    void requestedRoomWithOnlyAdjacentStayIsAssignable() throws Exception {
        booking("BK-ROOM-ADJACENT", CHECK_IN.minusDays(1), CHECK_IN, occupiedRoom,
                BookingStatus.DA_XAC_NHAN);
        Booking target = booking("BK-ROOM-TARGET-ADJ", CHECK_IN, CHECK_IN.plusDays(1), temporaryRoom,
                BookingStatus.DA_XAC_NHAN);

        mvc.perform(put("/api/bookings/{id}/room", target.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":" + occupiedRoom.getId() + "}"))
                .andExpect(status().isOk());

        assertThat(bookings.findById(target.getId()).orElseThrow().getRoom().getId()).isEqualTo(occupiedRoom.getId());
        assertThat(bookings.findById(target.getId()).orElseThrow().getRoomConfirmedAt()).isNotNull();
        assertThat(jdbc.queryForObject("select room_confirmed_by_user_id from bookings where id = ?",
                Long.class, target.getId())).isEqualTo(jdbc.queryForObject(
                        "select id from users where email = ?", Long.class, "room-assignment-it@homestay.local"));

        mvc.perform(put("/api/bookings/{id}/room", target.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":" + temporaryRoom.getId() + "}"))
                .andExpect(status().isConflict());
    }

    @Test
    void confirmingCurrentTemporaryRoomRecordsActorAndTimestamp() throws Exception {
        Booking target = booking("BK-ROOM-SAME", CHECK_IN, CHECK_IN.plusDays(1), temporaryRoom,
                BookingStatus.DA_XAC_NHAN);

        mvc.perform(put("/api/bookings/{id}/room", target.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":" + temporaryRoom.getId() + "}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/bookings")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].roomConfirmedBy").value("Receptionist"));

        Booking confirmed = bookings.findById(target.getId()).orElseThrow();
        assertThat(confirmed.getRoom().getId()).isEqualTo(temporaryRoom.getId());
        assertThat(confirmed.getRoomConfirmedAt()).isNotNull();
        assertThat(jdbc.queryForObject("select room_confirmed_by_user_id from bookings where id = ?",
                Long.class, target.getId())).isNotNull();
    }

    @Test
    void bookingListLoadsConfirmerForMultipleBookings() throws Exception {
        Booking confirmed = booking("BK-ROOM-LIST-CONFIRMED", CHECK_IN, CHECK_IN.plusDays(1), temporaryRoom,
                BookingStatus.DA_XAC_NHAN);
        confirmed.setRoomConfirmedAt(OffsetDateTime.now());
        confirmed.setRoomConfirmedByUser(users.getReferenceById(
                jdbc.queryForObject("select id from users where email = ?", Long.class,
                        "room-assignment-it@homestay.local")));
        bookings.saveAndFlush(confirmed);
        booking("BK-ROOM-LIST-OTHER", CHECK_IN.plusDays(2), CHECK_IN.plusDays(3), secondTemporaryRoom,
                BookingStatus.DA_XAC_NHAN);

        mvc.perform(get("/api/bookings")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[?(@.bookingCode == 'BK-ROOM-LIST-CONFIRMED')].roomConfirmedBy")
                        .value(org.hamcrest.Matchers.contains("Receptionist")))
                .andExpect(jsonPath("$.content[?(@.bookingCode == 'BK-ROOM-LIST-OTHER')].roomConfirmedBy")
                        .value(org.hamcrest.Matchers.contains((Object) null)));
    }

    @Test
    void multipleNightOverlapIsRejectedButCheckoutEqualsCheckinIsAllowed() throws Exception {
        booking("BK-ROOM-MULTI", CHECK_IN, CHECK_IN.plusDays(3), occupiedRoom,
                BookingStatus.DA_XAC_NHAN);
        Booking overlapping = booking("BK-ROOM-TARGET-MULTI", CHECK_IN.plusDays(2), CHECK_IN.plusDays(4), null,
                BookingStatus.DA_XAC_NHAN);

        mvc.perform(put("/api/bookings/{id}/room", overlapping.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":" + occupiedRoom.getId() + "}"))
                .andExpect(status().isConflict());
        assertThat(bookings.findById(overlapping.getId()).orElseThrow().getRoom()).isNull();

        Booking adjacent = booking("BK-ROOM-TARGET-NEXT", CHECK_IN.plusDays(3), CHECK_IN.plusDays(4), null,
                BookingStatus.DA_XAC_NHAN);
        mvc.perform(put("/api/bookings/{id}/room", adjacent.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":" + occupiedRoom.getId() + "}"))
                .andExpect(status().isOk());
        assertThat(bookings.findById(adjacent.getId()).orElseThrow().getRoom().getId()).isEqualTo(occupiedRoom.getId());
    }

    @Test
    void concurrentAssignmentsToOneRoom_allowOnlyOneAndKeepLoserTemporaryRoom() throws Exception {
        Booking first = booking("BK-ROOM-RACE-A", CHECK_IN, CHECK_IN.plusDays(2), temporaryRoom,
                BookingStatus.DA_XAC_NHAN);
        Booking second = booking("BK-ROOM-RACE-B", CHECK_IN, CHECK_IN.plusDays(2), secondTemporaryRoom,
                BookingStatus.DA_XAC_NHAN);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var one = pool.submit(() -> assignAfter(start, first.getId()));
            var two = pool.submit(() -> assignAfter(start, second.getId()));
            start.countDown();
            int statusOne = one.get(10, TimeUnit.SECONDS);
            int statusTwo = two.get(10, TimeUnit.SECONDS);

            assertThat(List.of(statusOne, statusTwo)).containsExactlyInAnyOrder(200, 409);
            long assignedToTarget = bookings.findAll().stream()
                    .filter(booking -> booking.getRoom() != null
                            && booking.getRoom().getId().equals(occupiedRoom.getId()))
                    .count();
            assertThat(assignedToTarget).isEqualTo(1);
            Booking firstAfter = bookings.findById(first.getId()).orElseThrow();
            Booking secondAfter = bookings.findById(second.getId()).orElseThrow();
            assertThat(firstAfter.getRoom().getId().equals(secondAfter.getRoom().getId())).isFalse();
        } finally {
            pool.shutdownNow();
        }
    }

    private int assignAfter(CountDownLatch start, Long bookingId) throws Exception {
        start.await();
        return mvc.perform(put("/api/bookings/{id}/room", bookingId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roomId\":" + occupiedRoom.getId() + "}"))
                .andReturn().getResponse().getStatus();
    }

    private Room room(String number) {
        Room room = new Room();
        room.setRoomNumber(number);
        room.setFloor(3);
        room.setRoomType(roomType.getName());
        room.setStatus(RoomStatus.TRONG_SACH);
        room.setActive(true);
        return rooms.saveAndFlush(room);
    }

    private Booking booking(String code, LocalDate checkIn, LocalDate checkOut, Room room, BookingStatus status) {
        Booking booking = new Booking();
        booking.setRoomType(roomType);
        booking.setRoom(room);
        booking.setRoomTypeNameSnapshot(roomType.getName());
        booking.setCheckInDate(checkIn);
        booking.setCheckOutDate(checkOut);
        booking.setWeekdayPriceSnapshot(100_000);
        booking.setWeekendPriceSnapshot(100_000);
        booking.setWeekendDaysSnapshot("SATURDAY,SUNDAY");
        booking.setTotalAmount(100_000);
        booking.setCreatedAt(OffsetDateTime.now());
        booking.setBookingCode(code);
        booking.setGuestName("Integration Test Guest");
        booking.setStatus(status);
        booking.setHoldExpiresAt(status == BookingStatus.CHO_XAC_NHAN ? OffsetDateTime.now().plusHours(2) : null);
        return bookings.saveAndFlush(booking);
    }
}
