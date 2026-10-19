package com.ttcs.homestay.security;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

/** S4-07 Lát 1: quyền truy cập API thống kê doanh thu. Chủ, Admin, Lễ tân xem được; Buồng phòng và khách thì không. */
@SpringBootTest
@AutoConfigureMockMvc
class RevenueReportAuthorizationTest {

    private static final String URL = "/api/reports/revenue?startDate=2088-07-01&endDate=2088-07-31";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        register(201L, "OWNER", "owner-revenue-token");
        register(202L, "ADMIN", "admin-revenue-token");
        register(203L, "RECEPTIONIST", "receptionist-revenue-token");
        register(204L, "HOUSEKEEPING", "housekeeping-revenue-token");
    }

    private void register(long id, String roleCode, String token) {
        Role role = mock(Role.class);
        when(role.getCode()).thenReturn(roleCode);

        User user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(user.isMustChangePassword()).thenReturn(false);
        when(user.getRole()).thenReturn(role);
        when(user.getTokenVersion()).thenReturn(0);
        when(userRepository.findWithRoleById(id)).thenReturn(Optional.of(user));

        Instant now = Instant.now();
        when(jwtDecoder.decode(token)).thenReturn(Jwt.withTokenValue("test-token-" + id)
                .header("alg", "HS256")
                .issuer("homestay")
                .subject(String.valueOf(id))
                .audience(List.of("homestay-api"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("typ", "access")
                .claim("role", roleCode)
                .claim("tv", 0)
                .build());
    }

    @Test
    void chuHomestayXemDuocThongKe() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", "Bearer owner-revenue-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRevenue").exists());
    }

    @Test
    void adminXemDuocThongKe() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", "Bearer admin-revenue-token"))
                .andExpect(status().isOk());
    }

    @Test
    void leTanXemDuocThongKeHomNay() throws Exception {
        LocalDate today = LocalDate.now(
                ZoneId.of("Asia/Ho_Chi_Minh"));

        String url = "/api/reports/revenue?startDate="
                + today
                + "&endDate="
                + today;

        mockMvc.perform(
                get(url)
                        .header(
                                "Authorization",
                                "Bearer receptionist-revenue-token"))
                .andExpect(status().isOk());
    }
    @Test
    void leTanKhongDuocXemDoanhThuHomQua() throws Exception {
        LocalDate yesterday = LocalDate.now(
                ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1);

        String url = "/api/reports/revenue?startDate="
                + yesterday
                + "&endDate="
                + yesterday;

        mockMvc.perform(
                get(url)
                        .header(
                                "Authorization",
                                "Bearer receptionist-revenue-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void leTanKhongDuocLocLoaiPhongNgoaiHomNay()
            throws Exception {

        LocalDate yesterday = LocalDate.now(
                ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1);

        String url = "/api/reports/revenue/by-room-type"
                + "?startDate=" + yesterday
                + "&endDate=" + yesterday;

        mockMvc.perform(
                get(url)
                        .header(
                                "Authorization",
                                "Bearer receptionist-revenue-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void chuHomestayXemDuocDoanhThuTheoNgay() throws Exception {
        mockMvc.perform(get("/api/reports/revenue/by-day")
                        .param("startDate", "2088-07-01")
                        .param("endDate", "2088-07-31")
                        .header("Authorization", "Bearer owner-revenue-token"))
                .andExpect(status().isOk());
    }

    @Test
    void adminVaLeTanXemDuocDoanhThuTheoNgay() throws Exception {
        for (String token : List.of("admin-revenue-token", "receptionist-revenue-token")) {
            mockMvc.perform(get("/api/reports/revenue/by-day")
                            .param("startDate", LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString())
                            .param("endDate", LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).toString())
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void leTanKhongDuocXemBieuDoNgayNgoaiHomNay() throws Exception {
        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(1);
        mockMvc.perform(get("/api/reports/revenue/by-day")
                        .param("startDate", yesterday.toString())
                        .param("endDate", yesterday.toString())
                        .header("Authorization", "Bearer receptionist-revenue-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void leTanKhongDuocXemSoSanhKyTruoc()
            throws Exception {

        LocalDate today = LocalDate.now(
                ZoneId.of("Asia/Ho_Chi_Minh"));

        String url = "/api/reports/comparison"
                + "?startDate=" + today
                + "&endDate=" + today;

        mockMvc.perform(
                get(url)
                        .header(
                                "Authorization",
                                "Bearer receptionist-revenue-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void buongPhongBiChan403() throws Exception {
        mockMvc.perform(get(URL).header("Authorization", "Bearer housekeeping-revenue-token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void chuaDangNhapBiChan401() throws Exception {
        mockMvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void khoangNgayKhongHopLe_tra400KemThongBao() throws Exception {
        mockMvc.perform(get("/api/reports/revenue?startDate=2088-07-31&endDate=2088-07-01")
                        .header("Authorization", "Bearer owner-revenue-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ngày kết thúc phải sau hoặc bằng ngày bắt đầu."));
    }

    @Test
    void ngaySaiDinhDang_tra400() throws Exception {
        mockMvc.perform(get("/api/reports/revenue?startDate=abc&endDate=2088-07-01")
                        .header("Authorization", "Bearer owner-revenue-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ngày phải có dạng YYYY-MM-DD."));
    }
}
