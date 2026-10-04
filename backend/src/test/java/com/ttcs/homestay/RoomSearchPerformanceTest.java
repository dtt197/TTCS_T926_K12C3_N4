package com.ttcs.homestay;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RoomSearchPerformanceTest {

    private static final int ROOM_COUNT = 40;
    private static final int OCCUPIED_ROOM_COUNT = 5;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void tim30DemVoi40PhongVa5BookingDuoiHaiGiay() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        LocalDate checkIn = LocalDate.now().plusDays(10);
        LocalDate checkOut = checkIn.plusDays(30);
        RoomType roomType = createRoomType(suffix);
        roomType = roomTypeRepository.saveAndFlush(roomType);
        createRooms(roomType, suffix);
        createBookings(roomType, suffix, checkIn, checkOut);
        roomRepository.flush();
        bookingRepository.flush();

        MvcResult firstResponse = mockMvc.perform(get("/api/public/rooms/search")
                        .param("checkIn", checkIn.toString())
                        .param("checkOut", checkOut.toString())
                        .param("guestCount", "2"))
                .andExpect(status().isOk())
                .andReturn();
        assertFixtureAvailability(firstResponse, roomType.getId());

        long startedAt = System.nanoTime();
        MvcResult secondResponse = mockMvc.perform(get("/api/public/rooms/search")
                        .param("checkIn", checkIn.toString())
                        .param("checkOut", checkOut.toString())
                        .param("guestCount", "2"))
                .andExpect(status().isOk())
                .andReturn();
        assertFixtureAvailability(secondResponse, roomType.getId());
        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        System.out.printf(
                "Tra cứu %d đêm, %d phòng và %d booking: %d ms%n",
                ChronoUnit.DAYS.between(checkIn, checkOut),
                ROOM_COUNT,
                OCCUPIED_ROOM_COUNT,
                elapsedMillis
        );
        org.assertj.core.api.Assertions.assertThat(elapsedMillis)
                .as("Tra cứu thật qua API phải hoàn tất trong vòng 2 giây")
                .isLessThan(2_000);
    }

    private void assertFixtureAvailability(MvcResult response, Long roomTypeId) throws Exception {
        JsonNode results = objectMapper.readTree(response.getResponse().getContentAsString());
        List<Integer> fixtureAvailability = new ArrayList<>();
        for (JsonNode result : results) {
            if (result.path("roomTypeId").asLong() == roomTypeId) {
                fixtureAvailability.add(result.path("availableRooms").asInt());
            }
        }

        org.assertj.core.api.Assertions.assertThat(fixtureAvailability)
                .containsExactly(ROOM_COUNT - OCCUPIED_ROOM_COUNT);
    }

    private RoomType createRoomType(String suffix) {
        RoomType roomType = new RoomType();
        roomType.setCode("PERF_" + suffix);
        roomType.setName("Phòng hiệu năng " + suffix);
        roomType.setStandardCapacity(2);
        roomType.setMaxCapacity(4);
        roomType.setNumberOfBeds(1);
        roomType.setWeekdayPrice(500_000L);
        roomType.setWeekendPrice(700_000L);
        roomType.setStatus(true);
        return roomType;
    }

    private void createRooms(RoomType roomType, String suffix) {
        List<Room> rooms = new ArrayList<>();
        for (int index = 1; index <= ROOM_COUNT; index++) {
            Room room = new Room();
            room.setRoomNumber("P" + suffix + index);
            room.setFloor(1);
            room.setRoomType(roomType.getName());
            room.setStatus(RoomStatus.TRONG_SACH);
            room.setActive(true);
            rooms.add(room);
        }
        roomRepository.saveAll(rooms);
    }

    private void createBookings(
            RoomType roomType,
            String suffix,
            LocalDate checkIn,
            LocalDate checkOut
    ) {
        List<Booking> bookings = new ArrayList<>();
        for (int index = 1; index <= OCCUPIED_ROOM_COUNT; index++) {
            Booking booking = new Booking();
            booking.setBookingCode("PERF-" + suffix + "-" + index);
            booking.setGuestName("Khách hiệu năng " + index);
            booking.setStatus(BookingStatus.DA_XAC_NHAN);
            booking.setRoomType(roomType);
            booking.setRoomTypeNameSnapshot(roomType.getName());
            booking.setCheckInDate(checkIn);
            booking.setCheckOutDate(checkOut);
            booking.setWeekdayPriceSnapshot(500_000L);
            booking.setWeekendPriceSnapshot(700_000L);
            booking.setWeekendDaysSnapshot("FRIDAY,SATURDAY");
            booking.setTotalAmount(15_000_000L);
            booking.setGuestCount(2);
            booking.setCreatedAt(OffsetDateTime.now());
            bookings.add(booking);
        }
        bookingRepository.saveAll(bookings);
    }
}
