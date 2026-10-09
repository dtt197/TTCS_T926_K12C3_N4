package com.ttcs.homestay.controller.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.service.BookingService;
import com.ttcs.homestay.service.GuestBookingService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Ma trận phân quyền (Excel): "Vận hành booking" và "Đặt phòng" – Lễ tân F, Chủ R, Admin R.
 * Chủ homestay và Admin chỉ được xem booking, không được tạo / xác nhận / cập nhật / đặt tại quầy.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BookingOperationRoleTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private GuestBookingService guestBookingService;

    @MockitoBean
    private com.ttcs.homestay.service.RoomShortageAlertService roomShortageAlertService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        mockUser(201L, "RECEPTIONIST");
        mockUser(203L, "ADMIN");
        mockUser(204L, "OWNER");
        when(jwtDecoder.decode("receptionist-token")).thenReturn(createJwt(201L, "RECEPTIONIST"));
        when(jwtDecoder.decode("admin-token")).thenReturn(createJwt(203L, "ADMIN"));
        when(jwtDecoder.decode("owner-token")).thenReturn(createJwt(204L, "OWNER"));
    }

    private void mockUser(long id, String roleCode) {
        Role role = mock(Role.class);
        when(role.getCode()).thenReturn(roleCode);
        User user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(user.isMustChangePassword()).thenReturn(false);
        when(user.getRole()).thenReturn(role);
        when(user.getTokenVersion()).thenReturn(0);
        when(userRepository.findWithRoleById(id)).thenReturn(Optional.of(user));
    }

    private Jwt createJwt(long userId, String roleCode) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("jwt-token-" + userId)
                .header("alg", "HS256")
                .issuer("homestay")
                .subject(String.valueOf(userId))
                .audience(List.of("homestay-api"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("typ", "access")
                .claim("role", roleCode)
                .claim("tv", 0)
                .build();
    }

    private static String walkInBody() {
        LocalDate checkIn = LocalDate.now().plusDays(1);
        return "{\"roomTypeId\":1,\"checkInDate\":\"" + checkIn + "\",\"checkOutDate\":\"" + checkIn.plusDays(1)
                + "\",\"guestName\":\"Khách tại quầy\",\"phone\":\"0912345678\",\"guestCount\":1}";
    }

    private static List<MockHttpServletRequestBuilder> bookingOperations() {
        return List.of(
                post("/api/bookings"),
                post("/api/bookings/5/preview"),
                put("/api/bookings/5"),
                put("/api/bookings/5/confirm"),
                post("/api/public/bookings/walk-in"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner-token", "admin-token"})
    void chuVaAdminChiDuocXem_khongThaoTacDuocBooking(String token) throws Exception {
        for (MockHttpServletRequestBuilder request : bookingOperations()) {
            mockMvc.perform(request
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void leTanDuocDatPhongTaiQuay() throws Exception {
        mockMvc.perform(post("/api/public/bookings/walk-in")
                        .header("Authorization", "Bearer receptionist-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(walkInBody()))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
    }
}