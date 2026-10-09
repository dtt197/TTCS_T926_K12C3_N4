package com.ttcs.homestay.controller.booking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/** S3-02 Lát 3: chỉ Lễ tân được huỷ booking, bắt buộc có lý do. */
@SpringBootTest
@AutoConfigureMockMvc
class BookingCancelControllerTest {

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
        mockUser(203L, "ADMIN");
        mockUser(204L, "OWNER");
        when(jwtDecoder.decode("receptionist-token")).thenReturn(createJwt(201L, "RECEPTIONIST"));
        when(jwtDecoder.decode("housekeeping-token")).thenReturn(createJwt(202L, "HOUSEKEEPING"));
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

    @Test
    void leTanHuyBookingCoLyDo_thanhCong() throws Exception {
        mockMvc.perform(put("/api/bookings/7/cancel")
                        .header("Authorization", "Bearer receptionist-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Khách báo huỷ chuyến\"}"))
                .andExpect(status().isOk());
        verify(bookingService).cancelBooking(eq(7L), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"housekeeping-token", "owner-token", "admin-token"})
    void vaiTroKhacLeTan_bi403(String token) throws Exception {
        mockMvc.perform(put("/api/bookings/7/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Khách báo huỷ chuyến\"}"))
                .andExpect(status().isForbidden());
        verify(bookingService, never()).cancelBooking(any(), any());
    }

    @Test
    void thieuLyDo_bi400() throws Exception {
        mockMvc.perform(put("/api/bookings/7/cancel")
                        .header("Authorization", "Bearer receptionist-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
        verify(bookingService, never()).cancelBooking(any(), any());
    }
}