
package com.ttcs.homestay.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.UserRepository;

class SecurityAuthorizationTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final AccountStatusFilter filter = new AccountStatusFilter(userRepository);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(long userId, String roleCode) {
        Role role = mock(Role.class);
        when(role.getCode()).thenReturn(roleCode);

        User user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(user.isMustChangePassword()).thenReturn(false);
        when(user.getRole()).thenReturn(role);
        when(user.getTokenVersion()).thenReturn(0);

        when(userRepository.findWithRoleById(userId))
                .thenReturn(Optional.of(user));

        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(String.valueOf(userId))
                .claim("role", roleCode)
                .claim("tv", 0)
                .build();

        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @Test
    void adminHopLe_duocDiQuaAccountStatusFilter() throws Exception {
        loginAs(1L, "ADMIN");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/admin/users"),
                response,
                chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void leTanHopLe_duocDiQuaAccountStatusFilter() throws Exception {
        loginAs(2L, "RECEPTIONIST");

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/internal/me"),
                response,
                chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void tokenGiaMaoVaiTroAdmin_biTuChoi401() throws Exception {
        loginAs(3L, "RECEPTIONIST");

        Jwt forgedJwt = Jwt.withTokenValue("forged-token")
                .header("alg", "HS256")
                .subject("3")
                .claim("role", "ADMIN")
                .claim("tv", 0)
                .build();

        SecurityContextHolder.getContext()
                .setAuthentication(new JwtAuthenticationToken(forgedJwt));

        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(
                new MockHttpServletRequest("GET", "/api/admin/users"),
                response,
                chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("ROLE_CHANGED");
        assertThat(chain.getRequest()).isNull();
    }
}
