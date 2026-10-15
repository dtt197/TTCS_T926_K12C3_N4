package com.ttcs.homestay.controller.booking;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.dto.booking.BookingSurchargeListResponse;
import com.ttcs.homestay.dto.booking.BookingSurchargeRequest;
import com.ttcs.homestay.dto.booking.BookingSurchargeResponse;
import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.SurchargeType;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.service.BookingSurchargeService;
import java.time.Instant;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

/** S4-02 Lát 1: chỉ Lễ tân và Chủ homestay được thêm phụ thu. */
@SpringBootTest
@AutoConfigureMockMvc
class BookingSurchargeControllerTest {

    private static final String VALID_BODY = """
            {"type":"THEM_NGUOI","quantity":1,"nights":2,"note":"Thêm bạn"}
            """;

    @Autowired private MockMvc mockMvc;
    @MockitoBean private BookingSurchargeService surchargeService;
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
        when(surchargeService.addSurcharge(eq(5L), any(BookingSurchargeRequest.class))).thenReturn(listResponse());
        when(surchargeService.getSurcharges(5L)).thenReturn(listResponse());
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

    private BookingSurchargeListResponse listResponse() {
        return new BookingSurchargeListResponse(5L, true, 3, 150_000L, 100_000L,
                List.of(new BookingSurchargeResponse(1L, SurchargeType.THEM_NGUOI, 1, 150_000L, 2, 300_000L,
                        "Thêm bạn", "201 Test Actor", OffsetDateTime.parse("2026-10-15T10:00:00Z"))),
                300_000L);
    }

    private MockHttpServletRequestBuilder addRequest(String token, String body) {
        var request = post("/api/bookings/5/surcharges").contentType(MediaType.APPLICATION_JSON).content(body);
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    @Test
    void leTanThemDuocPhuThu() throws Exception {
        mockMvc.perform(addRequest("receptionist-token", VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].amount").value(300000))
                .andExpect(jsonPath("$.totalAmount").value(300000));
    }

    @Test
    void chuHomestayThemDuocPhuThu() throws Exception {
        mockMvc.perform(addRequest("owner-token", VALID_BODY)).andExpect(status().isCreated());
    }

    @Test
    void buongPhongBiTuChoi() throws Exception {
        mockMvc.perform(addRequest("housekeeping-token", VALID_BODY)).andExpect(status().isForbidden());
        verify(surchargeService, never()).addSurcharge(any(), any());
    }

    @Test
    void adminChiXem_khongThemDuoc() throws Exception {
        mockMvc.perform(addRequest("admin-token", VALID_BODY)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/bookings/5/surcharges").header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAmount").value(300000));
    }

    @Test
    void chuaDangNhapBiTuChoi() throws Exception {
        mockMvc.perform(addRequest(null, VALID_BODY)).andExpect(status().isUnauthorized());
    }

    @Test
    void soLuong0_tra400VaThongBao() throws Exception {
        mockMvc.perform(addRequest("receptionist-token", """
                        {"type":"THEM_NGUOI","quantity":0,"nights":1}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Số lượng phải từ 1 trở lên"));
        verify(surchargeService, never()).addSurcharge(any(), any());
    }

    @Test
    void trangThaiKhongChoPhep_tra409VaThongBao() throws Exception {
        when(surchargeService.addSurcharge(eq(5L), any(BookingSurchargeRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                        "Chỉ thêm phụ thu cho booking đã xác nhận hoặc đang ở"));

        mockMvc.perform(addRequest("receptionist-token", VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Chỉ thêm phụ thu cho booking đã xác nhận hoặc đang ở"));
    }
}