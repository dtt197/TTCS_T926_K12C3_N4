package com.ttcs.homestay.controller.booking;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** S2-08 Lát 3: dữ liệu tra cứu qua API công khai có kèm giờ nhận phòng và giờ trả phòng (H2). */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingLookupStayTimesIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @BeforeEach
    void setUp() {
        RoomType doi = new RoomType();
        doi.setCode("TEST_TIMES");
        doi.setName("Phòng đôi (test giờ)");
        doi.setStandardCapacity(2);
        doi.setMaxCapacity(3);
        doi.setNumberOfBeds(1);
        doi = roomTypeRepository.save(doi);

        Booking booking = new Booking();
        booking.setBookingCode("TIME2345");
        booking.setGuestName("Khách test");
        booking.setGuestEmail("khach@example.com");
        booking.setStatus(BookingStatus.DA_XAC_NHAN);
        booking.setRoomType(doi);
        booking.setRoomTypeNameSnapshot(doi.getName());
        booking.setCheckInDate(LocalDate.of(2027, 6, 10));
        booking.setCheckOutDate(LocalDate.of(2027, 6, 13));
        booking.setWeekdayPriceSnapshot(500_000L);
        booking.setWeekendPriceSnapshot(700_000L);
        booking.setWeekendDaysSnapshot("FRIDAY,SATURDAY");
        booking.setTotalAmount(1_500_000L);
        booking.setCreatedAt(OffsetDateTime.now());
        bookingRepository.save(booking);
    }

    @Test
    void traCuuDung_coGioNhanPhongVaGioTraPhong() throws Exception {
        mockMvc.perform(post("/api/public/bookings/lookup")
                        .with(request -> {
                            request.setRemoteAddr("10.0.1.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookingCode\":\"TIME2345\",\"email\":\"khach@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkInTime").value(matchesPattern("\\d{2}:\\d{2}(:\\d{2})?")))
                .andExpect(jsonPath("$.checkOutTime").value(matchesPattern("\\d{2}:\\d{2}(:\\d{2})?")));
    }
}