package com.ttcs.homestay.controller.booking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.dto.booking.CustomerNoShowResponse;
import com.ttcs.homestay.dto.booking.NoShowEligibilityResponse;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.service.BookingNoShowService;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BookingNoShowControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingNoShowService noShowService;

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
                .claim("email", roleCode.toLowerCase() + "@test.local")
                .build();
    }

    @Test
    void checkEligibility_receptionist_returnsEligibility() throws Exception {
        OffsetDateTime now = OffsetDateTime.now(BookingNoShowService.ZONE_VN);
        NoShowEligibilityResponse eligibility = new NoShowEligibilityResponse(
                10L, "BK-000010", true, "Đủ điều kiện",
                now.minusHours(1), now.minusMinutes(30), now, "DA_XAC_NHAN", false
        );

        when(noShowService.checkEligibility(10L)).thenReturn(eligibility);

        mockMvc.perform(get("/api/bookings/10/no-show/eligibility")
                        .header("Authorization", "Bearer receptionist-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eligible").value(true))
                .andExpect(jsonPath("$.bookingCode").value("BK-000010"));
    }

    @Test
    void markNoShow_receptionist_returnsSuccess() throws Exception {
        BookingResponse response = new BookingResponse(
                10L, "BK-000010", "Nguyen Van A", 1L, "Deluxe",
                LocalDate.now(), LocalDate.now().plusDays(1),
                500000L, 600000L, "SATURDAY,SUNDAY", 500000L,
                BookingStatus.NO_SHOW, OffsetDateTime.now(), false, "0987654321", null, null, null
        );

        when(noShowService.markNoShow(eq(10L), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/bookings/10/no-show")
                        .header("Authorization", "Bearer receptionist-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"))
                .andExpect(jsonPath("$.bookingCode").value("BK-000010"));

        verify(noShowService).markNoShow(eq(10L), any(), any());
    }

    @Test
    void markNoShow_appointmentAlias_works() throws Exception {
        BookingResponse response = new BookingResponse(
                11L, "BK-000011", "Tran Thi B", 1L, "Deluxe",
                LocalDate.now(), LocalDate.now().plusDays(1),
                500000L, 600000L, "SATURDAY,SUNDAY", 500000L,
                BookingStatus.NO_SHOW, OffsetDateTime.now(), false, "0987654322", null, null, null
        );

        when(noShowService.markNoShow(eq(11L), any(), any())).thenReturn(response);

        mockMvc.perform(post("/api/appointments/11/no-show")
                        .header("Authorization", "Bearer receptionist-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    void markNoShow_unauthorized_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/bookings/10/no-show"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getCustomerNoShowHistory_receptionist_returnsSuccess() throws Exception {
        CustomerNoShowResponse response = new CustomerNoShowResponse(
                "0987654321", "Nguyen Van A", 2, true, "⚠️ Khách hàng này đã bỏ hẹn 2 lần"
        );
        when(noShowService.getCustomerNoShowHistory("0987654321")).thenReturn(response);

        mockMvc.perform(get("/api/customers/no-show-history")
                        .param("phone", "0987654321")
                        .header("Authorization", "Bearer receptionist-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("0987654321"))
                .andExpect(jsonPath("$.noShowCount").value(2))
                .andExpect(jsonPath("$.hasWarning").value(true))
                .andExpect(jsonPath("$.warningMessage").value("⚠️ Khách hàng này đã bỏ hẹn 2 lần"));
    }

    @Test
    void getCustomerNoShowHistory_aliasEndpoint_works() throws Exception {
        CustomerNoShowResponse response = new CustomerNoShowResponse(
                "0912345678", "Tran Thi B", 1, true, "⚠️ Khách hàng này đã bỏ hẹn 1 lần"
        );
        when(noShowService.getCustomerNoShowHistory("0912345678")).thenReturn(response);

        mockMvc.perform(get("/api/bookings/customer-no-show")
                        .param("phone", "0912345678")
                        .header("Authorization", "Bearer receptionist-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.noShowCount").value(1))
                .andExpect(jsonPath("$.hasWarning").value(true));
    }
}
