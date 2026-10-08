package com.ttcs.homestay.controller.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.dto.booking.BookingChangePreviewResponse;
import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.dto.booking.BookingUpdateRequest;
import com.ttcs.homestay.exception.RoomUnavailableException;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.service.BookingService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
class BookingControllerTest {

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
        when(jwtDecoder.decode("admin-token")).thenReturn(createJwt(203L, "ADMIN"));
        when(jwtDecoder.decode("owner-token")).thenReturn(createJwt(204L, "OWNER"));

        when(jwtDecoder.decode("receptionist-token"))
                .thenReturn(createJwt(201L, "RECEPTIONIST"));
        when(jwtDecoder.decode("housekeeping-token"))
                .thenReturn(createJwt(202L, "HOUSEKEEPING"));
    }

    private void mockUser(long id, String roleCode) {
        Role role = mock(Role.class);
        when(role.getCode()).thenReturn(roleCode);

        User user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(user.isMustChangePassword()).thenReturn(false);
        when(user.getRole()).thenReturn(role);
        when(user.getTokenVersion()).thenReturn(0);

        when(userRepository.findWithRoleById(id))
                .thenReturn(Optional.of(user));
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

    @ParameterizedTest
    @ValueSource(strings = {"receptionist-token", "admin-token", "owner-token"})
    void authorizedRolesCanUpdateConfirmedBooking(String token) throws Exception {
        BookingResponse mockResponse = new BookingResponse(
                5L,
                "BK-998877",
                "Nguyễn Văn An",
                2L,
                "Phòng Đôi Hướng Biển",
                LocalDate.of(2027, 7, 10),
                LocalDate.of(2027, 7, 15),
                600_000L,
                800_000L,
                "FRIDAY,SATURDAY",
                3_000_000L,
                BookingStatus.DA_XAC_NHAN,
                OffsetDateTime.now(),
                false
        );

        when(bookingService.updateBooking(eq(5L), any(BookingUpdateRequest.class)))
                .thenReturn(mockResponse);

        String jsonPayload = """
                {
                    "roomTypeId": 2,
                    "checkInDate": "2027-07-10",
                    "checkOutDate": "2027-07-15"
                }
                """;

        mockMvc.perform(put("/api/bookings/5")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.bookingCode").value("BK-998877"))
                .andExpect(jsonPath("$.roomTypeId").value(2))
                .andExpect(jsonPath("$.roomTypeNameSnapshot").value("Phòng Đôi Hướng Biển"))
                .andExpect(jsonPath("$.checkInDate").value("2027-07-10"))
                .andExpect(jsonPath("$.checkOutDate").value("2027-07-15"));

        verify(bookingService).updateBooking(eq(5L), any(BookingUpdateRequest.class));
    }

    @Test
    void leTanNhapNgayKhongHopLe_traVe400BadRequest() throws Exception {
        when(bookingService.updateBooking(eq(5L), any(BookingUpdateRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ngày trả phòng phải sau ngày nhận phòng"));

        String invalidDatesJson = """
                {
                    "roomTypeId": 2,
                    "checkInDate": "2027-07-15",
                    "checkOutDate": "2027-07-10"
                }
                """;

        mockMvc.perform(put("/api/bookings/5")
                        .header("Authorization", "Bearer receptionist-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidDatesJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void thieuTruongBatBuoc_traVe400BadRequest() throws Exception {
        // Thiếu roomTypeId
        String missingRoomTypeJson = """
                {
                    "checkInDate": "2027-07-10",
                    "checkOutDate": "2027-07-15"
                }
                """;

        mockMvc.perform(put("/api/bookings/5")
                        .header("Authorization", "Bearer receptionist-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingRoomTypeJson))
                .andExpect(status().isBadRequest());

        // Thiếu checkInDate
        String missingCheckInJson = """
                {
                    "roomTypeId": 1,
                    "checkOutDate": "2027-07-15"
                }
                """;

        mockMvc.perform(put("/api/bookings/5")
                        .header("Authorization", "Bearer receptionist-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingCheckInJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void housekeepingGoiCapNhatBooking_biChan403Forbidden() throws Exception {
        String jsonPayload = """
                {
                    "roomTypeId": 2,
                    "checkInDate": "2027-07-10",
                    "checkOutDate": "2027-07-15"
                }
                """;

        mockMvc.perform(put("/api/bookings/5")
                        .header("Authorization", "Bearer housekeeping-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isForbidden());
    }

    @Test
    void khongDangNhapGoiCapNhatBooking_biChan401Unauthorized() throws Exception {
        String jsonPayload = """
                {
                    "roomTypeId": 2,
                    "checkInDate": "2027-07-10",
                    "checkOutDate": "2027-07-15"
                }
                """;

        mockMvc.perform(put("/api/bookings/5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"receptionist-token", "admin-token", "owner-token"})
    void authorizedRolesCanPreviewBooking(String token) throws Exception {
        BookingChangePreviewResponse previewResponse = new BookingChangePreviewResponse(
                5L,
                2L,
                "Phòng Suite Deluxe",
                LocalDate.of(2027, 7, 10),
                LocalDate.of(2027, 7, 15),
                5,
                3_500_000L,
                3,
                true,
                List.of()
        );

        when(bookingService.previewBookingChange(eq(5L), any(BookingUpdateRequest.class)))
                .thenReturn(previewResponse);

        String jsonPayload = """
                {
                    "roomTypeId": 2,
                    "checkInDate": "2027-07-10",
                    "checkOutDate": "2027-07-15"
                }
                """;

        mockMvc.perform(post("/api/bookings/5/preview")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingId").value(5))
                .andExpect(jsonPath("$.roomTypeId").value(2))
                .andExpect(jsonPath("$.roomTypeName").value("Phòng Suite Deluxe"))
                .andExpect(jsonPath("$.numberOfNights").value(5))
                .andExpect(jsonPath("$.totalAmount").value(3500000))
                .andExpect(jsonPath("$.availableRooms").value(3))
                .andExpect(jsonPath("$.available").value(true));
        verify(bookingService).previewBookingChange(eq(5L), any(BookingUpdateRequest.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"preview", "update"})
    void bookingChangesRequireAuthentication(String operation) throws Exception {
        mockMvc.perform(changeRequest(operation)).andExpect(status().isUnauthorized());
        org.mockito.Mockito.verifyNoInteractions(bookingService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"preview", "update"})
    void housekeepingCannotChangeBookings(String operation) throws Exception {
        mockMvc.perform(changeRequest(operation).header("Authorization", "Bearer housekeeping-token"))
                .andExpect(status().isForbidden());
        org.mockito.Mockito.verifyNoInteractions(bookingService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"preview", "update"})
    void temporaryPasswordRestrictionStillApplies(String operation) throws Exception {
        when(userRepository.findWithRoleById(201L).orElseThrow().isMustChangePassword()).thenReturn(true);
        mockMvc.perform(changeRequest(operation).header("Authorization", "Bearer receptionist-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        org.mockito.Mockito.verifyNoInteractions(bookingService);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder changeRequest(String operation) {
        return (operation.equals("preview") ? post("/api/bookings/5/preview") : put("/api/bookings/5"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"roomTypeId":2,"checkInDate":"2027-07-10","checkOutDate":"2027-07-15"}
                        """);
    }

    @Test
    void capNhatBookingKhiHetPhong_traVe409Conflict() throws Exception {
        when(bookingService.updateBooking(eq(5L), any(BookingUpdateRequest.class)))
                .thenThrow(new RoomUnavailableException("Loại phòng Deluxe đã hết phòng trống trong khoảng thời gian đã chọn"));

        String jsonPayload = """
                {
                    "roomTypeId": 2,
                    "checkInDate": "2027-07-10",
                    "checkOutDate": "2027-07-15"
                }
                """;

        mockMvc.perform(put("/api/bookings/5")
                        .header("Authorization", "Bearer receptionist-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Loại phòng Deluxe đã hết phòng trống trong khoảng thời gian đã chọn"));
    }

    @Test
    void leTanXemCanhBaoThieuPhong_traVe200VaDanhSachCanhBao() throws Exception {
        var alert1 = new com.ttcs.homestay.dto.booking.RoomShortageAlertResponse(
                LocalDate.of(2026, 10, 15),
                "DON",
                "Phòng đơn",
                3L,
                1L
        );
        var alert2 = new com.ttcs.homestay.dto.booking.RoomShortageAlertResponse(
                LocalDate.of(2026, 10, 15),
                "DOI",
                "Phòng đôi",
                4L,
                2L
        );
        when(roomShortageAlertService.getShortageAlerts()).thenReturn(List.of(alert1, alert2));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/bookings/shortage-alerts")
                        .header("Authorization", "Bearer receptionist-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].date").value("2026-10-15"))
                .andExpect(jsonPath("$[0].roomTypeCode").value("DON"))
                .andExpect(jsonPath("$[0].roomTypeName").value("Phòng đơn"))
                .andExpect(jsonPath("$[0].bookingCount").value(3))
                .andExpect(jsonPath("$[0].availableRooms").value(1))
                .andExpect(jsonPath("$[1].roomTypeCode").value("DOI"))
                .andExpect(jsonPath("$[1].bookingCount").value(4))
                .andExpect(jsonPath("$[1].availableRooms").value(2));
    }

    @Test
    void buongPhongKhongCoQuyenXemCanhBao_traVe403() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/bookings/shortage-alerts")
                        .header("Authorization", "Bearer housekeeping-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void chuaDangNhap_traVe401() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/bookings/shortage-alerts"))
                .andExpect(status().isUnauthorized());
    }
}
