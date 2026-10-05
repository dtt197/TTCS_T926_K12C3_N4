package com.ttcs.homestay.controller.booking;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.BookingLookupNotFoundException;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import com.ttcs.homestay.service.BookingLookupRateLimiter;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * S2-08 Lát 2: giới hạn 10 lần tra cứu sai từ một IP trong 15 phút, chạy qua API công khai (H2).
 * Mỗi test dùng địa chỉ IP riêng và xoá bộ đếm sau khi chạy để không ảnh hưởng test khác.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingLookupRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @Autowired
    private BookingLookupRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter.clear();

        RoomType doi = new RoomType();
        doi.setCode("TEST_LIMIT");
        doi.setName("Phòng đôi (test giới hạn)");
        doi.setStandardCapacity(2);
        doi.setMaxCapacity(3);
        doi.setNumberOfBeds(1);
        doi = roomTypeRepository.save(doi);

        Booking booking = new Booking();
        booking.setBookingCode("LIMT2345");
        booking.setGuestName("Khách test");
        booking.setGuestEmail("khach@example.com");
        booking.setStatus(BookingStatus.CHO_XAC_NHAN);
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

    @AfterEach
    void tearDown() {
        rateLimiter.clear();
    }

    private ResultActions lookup(String ip, String email) throws Exception {
        return mockMvc.perform(post("/api/public/bookings/lookup")
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"bookingCode\":\"LIMT2345\",\"email\":\"" + email + "\"}"));
    }

    private void wrongTimes(String ip, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            lookup(ip, "sai@example.com").andExpect(status().isNotFound());
        }
    }

    @Test
    void sai9Lan_vanNhanThongBaoChung_vaTraCuuDungVanDuoc() throws Exception {
        wrongTimes("10.0.0.1", 9);

        lookup("10.0.0.1", "khach@example.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingCode").value("LIMT2345"));
    }

    @Test
    void lanSaiThu10_vanNhanThongBaoChung() throws Exception {
        wrongTimes("10.0.0.2", 9);

        lookup("10.0.0.2", "sai@example.com")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(BookingLookupNotFoundException.MESSAGE));
    }

    @Test
    void sauLanSaiThu10_tuChoiKeCaKhiNhapDung_kemGioThuLai() throws Exception {
        wrongTimes("10.0.0.3", 10);

        lookup("10.0.0.3", "khach@example.com")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.message").value(startsWith(
                        "Bạn đã nhập sai mã booking hoặc email 10 lần trong 15 phút. Vui lòng thử lại sau ")))
                .andExpect(jsonPath("$.bookingCode").doesNotExist());

        lookup("10.0.0.3", "sai@example.com").andExpect(status().isTooManyRequests());
    }

    @Test
    void ipKhac_vanTraCuuBinhThuong() throws Exception {
        wrongTimes("10.0.0.4", 10);

        lookup("10.0.0.5", "khach@example.com").andExpect(status().isOk());
    }

    @Test
    void traCuuDung_khongBiTinhVaoSoLanSai() throws Exception {
        wrongTimes("10.0.0.6", 9);
        lookup("10.0.0.6", "khach@example.com").andExpect(status().isOk());

        // Lần sai thứ 10 vẫn được trả lời bình thường, sau đó mới bị chặn
        lookup("10.0.0.6", "sai@example.com").andExpect(status().isNotFound());
        lookup("10.0.0.6", "khach@example.com").andExpect(status().isTooManyRequests());
    }
}