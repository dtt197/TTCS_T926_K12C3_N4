
package com.ttcs.homestay.security;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityAuthorizationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private UserRepository userRepository;

    
@BeforeEach
void setUp() {
    mockUser(101L, "RECEPTIONIST");
    mockUser(102L, "ADMIN");
    mockUser(103L, "HOUSEKEEPING");
    mockUser(104L, "OWNER");

    when(jwtDecoder.decode("receptionist-test-token"))
            .thenReturn(createJwt(101L, "RECEPTIONIST"));

    when(jwtDecoder.decode("admin-test-token"))
            .thenReturn(createJwt(102L, "ADMIN"));

    when(jwtDecoder.decode("housekeeping-test-token"))
            .thenReturn(createJwt(103L, "HOUSEKEEPING"));

    when(jwtDecoder.decode("owner-test-token"))
            .thenReturn(createJwt(104L, "OWNER"));
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

        return Jwt.withTokenValue("test-token-" + userId)
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
    void leTanTruyCapApiAdmin_biChan403() throws Exception {
        mockMvc.perform(
                get("/api/admin/authorization-test")
                        .header(
                                "Authorization",
                                "Bearer receptionist-test-token"
                        )
        ).andExpect(status().isForbidden());
    }

    @Test
    void housekeepingTruyCapApiAdmin_biChan403() throws Exception {
        mockMvc.perform(
                get("/api/admin/authorization-test")
                        .header(
                                "Authorization",
                                "Bearer housekeeping-test-token"
                        )
        ).andExpect(status().isForbidden());
    }

    @Test
    void housekeepingTruyCapApiBaoTri_biChan403() throws Exception {
        mockMvc.perform(
                patch("/api/rooms/4/maintenance")
                        .header(
                                "Authorization",
                                "Bearer housekeeping-test-token"
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
        ).andExpect(status().isForbidden());
    }

    @Test
    void adminDuocDiQuaLopPhanQuyen() throws Exception {
        mockMvc.perform(
                get("/api/admin/authorization-test")
                        .header(
                                "Authorization",
                                "Bearer admin-test-token"
                        )
        ).andExpect(status().isNotFound());
    }

    @Test
    void chuaDangNhapTruyCapApiAdmin_biChan401() throws Exception {
        mockMvc.perform(
                get("/api/admin/authorization-test")
        ).andExpect(status().isUnauthorized());
    }
    @Test
void housekeepingTruyCapApiCheckIn_biChan403() throws Exception {
    mockMvc.perform(
            post("/api/rooms/4/check-in")
                    .header(
                            "Authorization",
                            "Bearer housekeeping-test-token"
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
    ).andExpect(status().isForbidden());
}

@Test
void housekeepingTruyCapApiCheckOut_biChan403() throws Exception {
    mockMvc.perform(
            post("/api/rooms/4/check-out")
                    .header(
                            "Authorization",
                            "Bearer housekeeping-test-token"
                    )
    ).andExpect(status().isForbidden());
}

@Test
void leTanThemLoaiPhong_biChan403() throws Exception {
    mockMvc.perform(
            post("/room-types/add")
                    .header("Authorization", "Bearer receptionist-test-token")
    ).andExpect(status().isForbidden());
}

@Test
void housekeepingXoaLoaiPhong_biChan403() throws Exception {
    mockMvc.perform(
            get("/room-types/delete/1")
                    .header("Authorization", "Bearer housekeeping-test-token")
    ).andExpect(status().isForbidden());
}

@Test
void ownerDuocTruyCapQuanLyLoaiPhong() throws Exception {
    mockMvc.perform(
            get("/room-types/add")
                    .header("Authorization", "Bearer owner-test-token")
    ).andExpect(status().isOk());
}

@Test
void adminDuocTruyCapQuanLyLoaiPhong() throws Exception {
    mockMvc.perform(
            get("/room-types/add")
                    .header("Authorization", "Bearer admin-test-token")
    ).andExpect(status().isOk());
}

@Test
void ownerCapNhatTrangThaiPhong_biChan403() throws Exception {
    mockMvc.perform(
            patch("/api/rooms/4/status")
                    .header("Authorization", "Bearer owner-test-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
    ).andExpect(status().isForbidden());
}

@Test
void adminTruyCapApiChuaDuocCapQuyen_biChan403() throws Exception {
    mockMvc.perform(
            get("/api/s1-04-forbidden-check")
                    .header("Authorization", "Bearer admin-test-token")
    ).andExpect(status().isForbidden());
}


}
