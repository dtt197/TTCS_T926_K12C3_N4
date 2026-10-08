package com.ttcs.homestay.controller.booking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.dto.booking.BookingDepositAdjustmentRequest;
import com.ttcs.homestay.dto.booking.BookingDepositAdjustmentResponse;
import com.ttcs.homestay.entity.DepositAdjustmentType;
import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.service.BookingDepositAdjustmentService;
import com.ttcs.homestay.service.BookingService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class BookingDepositAdjustmentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private BookingDepositAdjustmentService adjustmentService;
    @MockitoBean private BookingService bookingService;
    @MockitoBean private JdbcTemplate jdbcTemplate;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        mockActiveUser(201L, "RECEPTIONIST");
        mockActiveUser(204L, "OWNER");
        mockActiveUser(203L, "ADMIN");
        mockActiveUser(202L, "HOUSEKEEPING");
        when(jwtDecoder.decode("receptionist-token")).thenReturn(jwt("201", "RECEPTIONIST"));
        when(jwtDecoder.decode("owner-token")).thenReturn(jwt("204", "OWNER"));
        when(jwtDecoder.decode("admin-token")).thenReturn(jwt("203", "ADMIN"));
        when(jwtDecoder.decode("housekeeping-token")).thenReturn(jwt("202", "HOUSEKEEPING"));
    }

    private void mockActiveUser(long userId, String roleCode) {
        Role role = mock(Role.class);
        when(role.getCode()).thenReturn(roleCode);

        User user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(user.isMustChangePassword()).thenReturn(false);
        when(user.getRole()).thenReturn(role);
        when(user.getTokenVersion()).thenReturn(0);

        when(userRepository.findWithRoleById(userId)).thenReturn(Optional.of(user));
    }

    private Jwt jwt(String userId, String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("jwt-" + userId).header("alg", "HS256")
                .issuer("homestay").subject(userId).audience(List.of("homestay-api"))
                .issuedAt(now).expiresAt(now.plusSeconds(3600)).claim("typ", "access")
                .claim("role", role).claim("tv", 0).claim("email", userId + "@example.test")
                .claim("fullName", userId + " Test Actor")
                .build();
    }

    private BookingDepositAdjustmentResponse response() {
        return new BookingDepositAdjustmentResponse(17L, 5L, DepositAdjustmentType.TANG,
                new BigDecimal("250000"), "Thu thêm cọc", "receptionist@example.test",
                OffsetDateTime.parse("2026-10-08T10:00:00Z"), new BigDecimal("750000"));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createRequest(String token) {
        var request = post("/api/bookings/5/deposit-adjustments")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"type":"TANG","amount":250000,"reason":"Thu thêm cọc","createdBy":"attacker"}
                        """);
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    @Test
    void receptionistCanCreateAndGetsLedgerEntry() throws Exception {
        when(adjustmentService.createAdjustment(eq(5L), any(BookingDepositAdjustmentRequest.class)))
                .thenReturn(response());

        mockMvc.perform(createRequest("receptionist-token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(17))
                .andExpect(jsonPath("$.bookingId").value(5))
                .andExpect(jsonPath("$.adjustmentType").value("TANG"))
                .andExpect(jsonPath("$.amount").value(250000))
                .andExpect(jsonPath("$.currentDepositTotal").value(750000))
                .andExpect(jsonPath("$.createdBy").value("receptionist@example.test"));

        verify(adjustmentService).createAdjustment(eq(5L), argThat(request ->
                request.adjustmentType() == DepositAdjustmentType.TANG
                        && request.amount().compareTo(new BigDecimal("250000")) == 0
                        && "Thu th\u00eam c\u1ecdc".equals(request.reason())));
    }

    @Test
    void ownerCanCreate() throws Exception {
        when(adjustmentService.createAdjustment(eq(5L), any(BookingDepositAdjustmentRequest.class)))
                .thenReturn(response());
        mockMvc.perform(createRequest("owner-token")).andExpect(status().isCreated());
        verify(adjustmentService).createAdjustment(eq(5L), any(BookingDepositAdjustmentRequest.class));
    }

    @Test
    void adminIsForbidden() throws Exception {
        mockMvc.perform(createRequest("admin-token")).andExpect(status().isForbidden());
        verifyNoInteractions(adjustmentService);
    }

    @Test
    void housekeepingIsForbidden() throws Exception {
        mockMvc.perform(createRequest("housekeeping-token")).andExpect(status().isForbidden());
        verifyNoInteractions(adjustmentService);
    }

    @Test
    void anonymousIsUnauthorized() throws Exception {
        mockMvc.perform(createRequest(null)).andExpect(status().isUnauthorized());
        verifyNoInteractions(adjustmentService);
    }

    @Test
    void bookingDetailsRemainReadableToAdminOwnerAndReceptionist() throws Exception {
        when(jdbcTemplate.query(eq("SELECT id, booking_code, status, hold_expires_at FROM bookings WHERE id = ?"),
                any(org.springframework.jdbc.core.RowMapper.class), eq(5L)))
                .thenReturn(List.of(java.util.Map.of("id", 5L, "bookingCode", "BK-5",
                        "status", "DA_XAC_NHAN")));
        for (String token : List.of("admin-token", "owner-token", "receptionist-token")) {
            mockMvc.perform(get("/api/bookings/5/details")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void bookingDetailsRemainForbiddenToHousekeepingAndAnonymous() throws Exception {
        mockMvc.perform(get("/api/bookings/5/details")
                        .header("Authorization", "Bearer housekeeping-token"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/bookings/5/details"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidTypeAmountAndReasonAreRejected() throws Exception {
        for (String json : List.of(
                "{\"type\":\"OTHER\",\"amount\":10,\"reason\":\"x\"}",
                "{\"type\":\"TANG\",\"amount\":0,\"reason\":\"x\"}",
                "{\"type\":\"TANG\",\"amount\":10,\"reason\":\" \"}",
                "{\"type\":\"TANG\",\"amount\":10,\"reason\":\"" + "x".repeat(501) + "\"}")) {
            mockMvc.perform(post("/api/bookings/5/deposit-adjustments")
                            .header("Authorization", "Bearer receptionist-token")
                            .contentType(MediaType.APPLICATION_JSON).content(json))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(adjustmentService);
    }

    @Test
    void adjustmentsCannotBeUpdatedOrDeleted() throws Exception {
        mockMvc.perform(put("/api/bookings/5/deposit-adjustments/17")
                        .header("Authorization", "Bearer receptionist-token")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/bookings/5/deposit-adjustments/17")
                        .header("Authorization", "Bearer receptionist-token"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(adjustmentService);
    }
}
