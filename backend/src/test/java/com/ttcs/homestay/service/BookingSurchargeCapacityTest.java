package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.dto.booking.BookingSurchargeListResponse;
import com.ttcs.homestay.dto.booking.BookingSurchargeRequest;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingGuest;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.entity.SurchargeType;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.BookingGuestRepository;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import com.ttcs.homestay.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** S4-02 Lát 3: không cho thêm người vượt sức chứa tối đa của loại phòng. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BookingSurchargeCapacityTest {

    @Autowired private BookingSurchargeService surchargeService;
    @Autowired private BookingRepository bookingRepository;
    @Autowired private RoomTypeRepository roomTypeRepository;
    @Autowired private BookingGuestRepository bookingGuestRepository;
    @Autowired private MockMvc mockMvc;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private UserRepository userRepository;

    private RoomType roomType;

    @BeforeEach
    void setUp() {
        roomType = new RoomType();
        roomType.setCode("S402CAP");
        roomType.setName("Phòng thử sức chứa");
        roomType.setStandardCapacity(2);
        roomType.setMaxCapacity(4);
        roomType.setNumberOfBeds(2);
        roomType.setStatus(true);
        roomType.setWeekdayPrice(500_000L);
        roomType.setWeekendPrice(700_000L);
        roomType.setExtraGuestFee(150_000L);
        roomType = roomTypeRepository.save(roomType);

        Role role = mock(Role.class);
        when(role.getCode()).thenReturn("RECEPTIONIST");
        User user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(user.isMustChangePassword()).thenReturn(false);
        when(user.getRole()).thenReturn(role);
        when(user.getTokenVersion()).thenReturn(0);
        when(userRepository.findWithRoleById(201L)).thenReturn(Optional.of(user));
        Instant now = Instant.now();
        when(jwtDecoder.decode("receptionist-token")).thenReturn(Jwt.withTokenValue("jwt-201").header("alg", "HS256")
                .issuer("homestay").subject("201").audience(List.of("homestay-api"))
                .issuedAt(now).expiresAt(now.plusSeconds(3600)).claim("typ", "access")
                .claim("role", "RECEPTIONIST").claim("tv", 0).claim("email", "201@example.test")
                .claim("fullName", "201 Test Actor").build());
    }

    private Booking saveBooking(String code, BookingStatus status, Integer guestCount) {
        Booking booking = new Booking();
        booking.setBookingCode(code);
        booking.setGuestName("Khách sức chứa");
        booking.setStatus(status);
        booking.setRoomType(roomType);
        booking.setRoomTypeNameSnapshot(roomType.getName());
        booking.setCheckInDate(LocalDate.of(2027, 6, 10));
        booking.setCheckOutDate(LocalDate.of(2027, 6, 13));
        booking.setWeekdayPriceSnapshot(500_000L);
        booking.setWeekendPriceSnapshot(700_000L);
        booking.setWeekendDaysSnapshot("FRIDAY,SATURDAY");
        booking.setTotalAmount(1_500_000L);
        booking.setGuestCount(guestCount);
        booking.setCreatedAt(OffsetDateTime.now());
        return bookingRepository.saveAndFlush(booking);
    }

    private static BookingSurchargeRequest themNguoi(int quantity) {
        return new BookingSurchargeRequest(SurchargeType.THEM_NGUOI, quantity, 1, null);
    }

    private static HttpStatus statusOf(Throwable error) {
        return HttpStatus.valueOf(((ResponseStatusException) error).getStatusCode().value());
    }

    @Test
    void toiDa4Dang3Khach_them1Nguoi_duocChapNhan() {
        Booking booking = saveBooking("S402CAP1", BookingStatus.DA_XAC_NHAN, 3);

        BookingSurchargeListResponse before = surchargeService.getSurcharges(booking.getId());
        assertThat(before.currentGuestCount()).isEqualTo(3);
        assertThat(before.maxCapacity()).isEqualTo(4);
        assertThat(before.remainingCapacity()).isEqualTo(1);

        BookingSurchargeListResponse after = surchargeService.addSurcharge(booking.getId(), themNguoi(1));
        assertThat(after.items()).hasSize(1);
        assertThat(after.currentGuestCount()).isEqualTo(4);
        assertThat(after.remainingCapacity()).isZero();
    }

    @Test
    void toiDa4Dang3Khach_them2Nguoi_biTuChoiKemThongBao() {
        Booking booking = saveBooking("S402CAP2", BookingStatus.DA_XAC_NHAN, 3);

        assertThatThrownBy(() -> surchargeService.addSurcharge(booking.getId(), themNguoi(2)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(statusOf(error)).isEqualTo(HttpStatus.CONFLICT))
                .hasMessageContaining("tối đa 4")
                .hasMessageContaining("đang có 3 khách");
        assertThat(surchargeService.getSurcharges(booking.getId()).items()).isEmpty();
    }

    @Test
    void themLanLuotHaiDongMoiDong1Nguoi_dongThuHaiBiTuChoi() {
        Booking booking = saveBooking("S402CAP3", BookingStatus.DA_XAC_NHAN, 3);

        surchargeService.addSurcharge(booking.getId(), themNguoi(1));

        assertThatThrownBy(() -> surchargeService.addSurcharge(booking.getId(), themNguoi(1)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("không thể thêm người");
        assertThat(surchargeService.getSurcharges(booking.getId()).items()).hasSize(1);
    }

    @Test
    void phongDaDuSucChua_khongThemNguoiNhungVanThemGiuong() {
        Booking booking = saveBooking("S402CAP4", BookingStatus.DA_XAC_NHAN, 4);

        assertThatThrownBy(() -> surchargeService.addSurcharge(booking.getId(), themNguoi(1)))
                .isInstanceOf(ResponseStatusException.class);

        BookingSurchargeListResponse result = surchargeService.addSurcharge(booking.getId(),
                new BookingSurchargeRequest(SurchargeType.THEM_GIUONG, 1, 1, null));
        assertThat(result.items()).hasSize(1);
        assertThat(result.currentGuestCount()).isEqualTo(4);
    }

    @Test
    void daNhanPhong_tinhTheoSoKhachDaDangKy() {
        Booking booking = saveBooking("S402CAP5", BookingStatus.DA_NHAN_PHONG, 1);
        for (int order = 0; order < 3; order++) {
            BookingGuest guest = new BookingGuest();
            guest.setBooking(booking);
            guest.setGuestOrder(order);
            guest.setFullName("Khách " + (order + 1));
            bookingGuestRepository.save(guest);
        }
        bookingGuestRepository.flush();

        assertThat(surchargeService.getSurcharges(booking.getId()).currentGuestCount()).isEqualTo(3);
        assertThatThrownBy(() -> surchargeService.addSurcharge(booking.getId(), themNguoi(2)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("đang có 3 khách");
    }

    @Test
    void goiThangApiBoQuaGiaoDien_vanBiTuChoi() throws Exception {
        Booking booking = saveBooking("S402CAP6", BookingStatus.DA_XAC_NHAN, 4);

        mockMvc.perform(post("/api/bookings/" + booking.getId() + "/surcharges")
                        .header("Authorization", "Bearer receptionist-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"THEM_NGUOI\",\"quantity\":1,\"nights\":1}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("tối đa 4")));
    }
}