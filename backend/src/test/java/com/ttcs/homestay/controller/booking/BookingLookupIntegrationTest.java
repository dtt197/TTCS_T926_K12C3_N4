package com.ttcs.homestay.controller.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.BookingLookupNotFoundException;
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

/**
 * S2-08 Lát 1: tra cứu booking qua API công khai trên CSDL thật (H2), không gửi token.
 * Kiểm tra dữ liệu trả về chỉ có các trường được phép.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingLookupIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RoomTypeRepository roomTypeRepository;

    @BeforeEach
    void setUp() {
        RoomType doi = new RoomType();
        doi.setCode("TEST_LOOKUP");
        doi.setName("Phòng đôi (test tra cứu)");
        doi.setStandardCapacity(2);
        doi.setMaxCapacity(3);
        doi.setNumberOfBeds(1);
        doi = roomTypeRepository.save(doi);

        Booking booking = new Booking();
        booking.setBookingCode("LKUP2345");
        booking.setGuestName("Nguyễn Văn A");
        booking.setGuestEmail("khach@example.com");
        booking.setGuestPhone("0912345678");
        booking.setNote("Ghi chú riêng của khách");
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

    private String body(String code, String email) {
        return "{\"bookingCode\":\"" + code + "\",\"email\":\"" + email + "\"}";
    }

    @Test
    void khachChuaDangNhap_dungMaVaEmail_xemDuocChiTiet() throws Exception {
        mockMvc.perform(post("/api/public/bookings/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("lkup2345", "Khach@Example.com")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingCode").value("LKUP2345"))
                .andExpect(jsonPath("$.statusLabel").value("Đã xác nhận"))
                .andExpect(jsonPath("$.roomTypeName").value("Phòng đôi (test tra cứu)"))
                .andExpect(jsonPath("$.checkInDate").value("2027-06-10"))
                .andExpect(jsonPath("$.checkOutDate").value("2027-06-13"))
                .andExpect(jsonPath("$.nights").value(3))
                .andExpect(jsonPath("$.totalAmount").value(1_500_000))
                .andExpect(jsonPath("$.depositAmount").value(0));
    }

    @Test
    void duLieuTraVe_khongCoThongTinNhayCam() throws Exception {
        String json = mockMvc.perform(post("/api/public/bookings/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("LKUP2345", "khach@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(json)
                .doesNotContain("0912345678")
                .doesNotContain("Ghi chú riêng của khách")
                .doesNotContain("khach@example.com")
                .doesNotContain("Nguyễn Văn A")
                .doesNotContainIgnoringCase("idNumber")
                .doesNotContainIgnoringCase("internalNote")
                .doesNotContain("\"note\"")
                .doesNotContain("\"id\"");
    }

    @Test
    void emailKhongKhop_404VaThongBaoChung() throws Exception {
        mockMvc.perform(post("/api/public/bookings/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("LKUP2345", "nguoikhac@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(BookingLookupNotFoundException.MESSAGE))
                .andExpect(jsonPath("$.bookingCode").doesNotExist());
    }

    @Test
    void maKhongTonTai_404VaCungThongBaoChung() throws Exception {
        mockMvc.perform(post("/api/public/bookings/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("ZZZZ9999", "khach@example.com")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(BookingLookupNotFoundException.MESSAGE));
    }

    @Test
    void boTrongMaHoacEmail_400() throws Exception {
        mockMvc.perform(post("/api/public/bookings/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vui lòng nhập mã booking và email"));
    }
}