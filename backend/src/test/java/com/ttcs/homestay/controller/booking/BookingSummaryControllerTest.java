package com.ttcs.homestay.controller.booking;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.service.BookingService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

/** S3-10 Lát 3: mở nhanh chi tiết booking từ sơ đồ phòng. */
@SpringBootTest
@AutoConfigureMockMvc
class BookingSummaryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private com.ttcs.homestay.service.RoomShortageAlertService roomShortageAlertService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        mockUser(201L, "RECEPTIONIST");
        mockUser(202L, "HOUSEKEEPING");
        when(jwtDecoder.decode("receptionist-token")).thenReturn(createJwt(201L, "RECEPTIONIST"));
        when(jwtDecoder.decode("housekeeping-token")).thenReturn(createJwt(202L, "HOUSEKEEPING"));
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

    @Test
    void leTanMoDuocChiTietBooking() throws Exception {
        mockMvc.perform(get("/api/bookings/7/summary").header("Authorization", "Bearer receptionist-token"))
                .andExpect(status().isOk());
        verify(bookingService).getBookingSummary(7L);
    }

    @Test
    void bookingKhongConTonTai_bao404() throws Exception {
        when(bookingService.getBookingSummary(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking không còn tồn tại"));

        mockMvc.perform(get("/api/bookings/99/summary").header("Authorization", "Bearer receptionist-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    void buongPhongKhongCoQuyenXem_bi403() throws Exception {
        mockMvc.perform(get("/api/bookings/7/summary").header("Authorization", "Bearer housekeeping-token"))
                .andExpect(status().isForbidden());
        verify(bookingService, never()).getBookingSummary(7L);
    }
}