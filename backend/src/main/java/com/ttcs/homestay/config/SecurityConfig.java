package com.ttcs.homestay.config;

import java.time.Duration;
import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.security.AccountStatusFilter;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            UserRepository userRepository
    ) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize

                        // API xác thực công khai
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/refresh",
                                "/api/auth/logout",
                                "/api/auth/forgot-password",
                                "/api/auth/reset-password"
                        ).permitAll()

                        // Thông tin phiên đăng nhập
                        .requestMatchers("/api/internal/**").authenticated()

                        // S1-05: nhật ký chỉ ADMIN được xem
                        .requestMatchers("/api/admin/audit-logs/**").hasRole("ADMIN")

                        // S1-04: ADMIN và OWNER được xem danh sách tài khoản
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/admin/users"
                        ).hasAnyRole("ADMIN", "OWNER")

                        // Các thao tác quản lý tài khoản chỉ dành cho ADMIN
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")

                        // Tài khoản cá nhân
                        .requestMatchers("/api/account/**").authenticated()

                        // S1-04: Xem danh sách và thông tin phòng
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/rooms",
                                "/api/rooms/**"
                        ).hasAnyRole(
                                "ADMIN",
                                "OWNER",
                                "RECEPTIONIST",
                                "HOUSEKEEPING"
                        )

                        // Cập nhật trạng thái phòng
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/rooms/*/status"
                        ).hasAnyRole(
                                "ADMIN",
                                "RECEPTIONIST",
                                "HOUSEKEEPING"
                        )

                        // Cập nhật bảo trì phòng
                        .requestMatchers(
                                HttpMethod.PATCH,
                                "/api/rooms/*/maintenance"
                        ).hasAnyRole(
                                "ADMIN",
                                "RECEPTIONIST"
                        )

                        // Check-in và check-out
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/rooms/*/check-in",
                                "/api/rooms/*/check-out"
                        ).hasAnyRole(
                                "ADMIN",
                                "RECEPTIONIST"
                        )

                        // Quản lý thông tin phòng
                        .requestMatchers("/api/room-management/**")
                        .hasAnyRole("ADMIN", "OWNER")

                        // S1-06: xem loại phòng cho 4 vai trò nội bộ (ma trận: R)
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/room-types",
                                "/api/room-types/**"
                        ).hasAnyRole(
                                "ADMIN",
                                "OWNER",
                                "RECEPTIONIST",
                                "HOUSEKEEPING"
                        )

                        // S1-06: thêm/sửa/ngừng bán/xoá loại phòng (ma trận: Chủ homestay và Admin = F)
                        .requestMatchers("/api/room-types/**")
                        .hasAnyRole("ADMIN", "OWNER")
                                                // S1-08: xem tiện nghi cho 4 vai trò nội bộ (ma trận: R)
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/amenities",
                                "/api/amenities/**"
                        ).hasAnyRole(
                                "ADMIN",
                                "OWNER",
                                "RECEPTIONIST",
                                "HOUSEKEEPING"
                        )

                        // S1-08: thêm/sửa/ngừng dùng/xoá tiện nghi (ma trận: Chủ homestay và Admin = F)
                        .requestMatchers("/api/amenities/**")
                        .hasAnyRole("ADMIN", "OWNER")
                        // S1-09: lịch sử thay đổi tham số (Chủ homestay, Quản trị)
                        .requestMatchers(HttpMethod.GET, "/api/settings/history")
                        .hasAnyRole("OWNER", "ADMIN")

                        // S1-09: xem tham số (ma trận "Bảng giá và chính sách huỷ": Lễ tân R, Admin R)
                        .requestMatchers(HttpMethod.GET, "/api/settings")
                        .hasAnyRole("OWNER", "ADMIN", "RECEPTIONIST")

                        // S1-09: sửa tham số chỉ Chủ homestay (ma trận: F)
                        .requestMatchers(HttpMethod.PUT, "/api/settings")
                        .hasRole("OWNER")
                                                // S2-02: xem giá đè (ma trận "Bảng giá và chính sách huỷ": Admin R, Lễ tân R)
                        .requestMatchers(HttpMethod.GET, "/api/price-overrides", "/api/price-overrides/**")
                        .hasAnyRole("OWNER", "ADMIN", "RECEPTIONIST")

                        // S2-02: thêm/sửa/xoá giá đè chỉ Chủ homestay (ma trận: F)
                        .requestMatchers("/api/price-overrides/**")
                        .hasRole("OWNER")

                        // S2-01 & S3-04: booking tạo và xem trước thay đổi bởi Admin, Chủ homestay hoặc Lễ tân.
                        .requestMatchers(HttpMethod.POST, "/api/bookings", "/api/bookings/*/preview")
                        .hasAnyRole("ADMIN", "OWNER", "RECEPTIONIST")

                        // S3-01: Le tan xac nhan booking va ghi nhan tien coc.
                        .requestMatchers(HttpMethod.PUT, "/api/bookings/*/confirm")
                        .hasAnyRole("ADMIN", "OWNER", "RECEPTIONIST")

                        // Lễ tân có thể cập nhật ngày lưu trú và loại phòng của booking hiện có.
                        .requestMatchers(HttpMethod.PUT, "/api/bookings/*")
                        .hasAnyRole("ADMIN", "OWNER", "RECEPTIONIST")
                        
                        // S2-02 Lát 2: xem giá từng đêm (ma trận "Bảng giá": Chủ F, Admin R, Lễ tân R)
                        .requestMatchers(HttpMethod.GET, "/api/pricing/**")
                        .hasAnyRole("OWNER", "ADMIN", "RECEPTIONIST")

                        .requestMatchers(
                          HttpMethod.GET,
                         "/api/bookings",
                         "/api/bookings/**"
)
                        .hasAnyRole("ADMIN", "OWNER", "RECEPTIONIST")
                        // S2-07 & S2-04: trang đặt phòng và danh mục/chi tiết loại phòng công khai, khách không cần đăng nhập
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/public/room-types",
                                "/api/public/room-types/**",
                                "/api/public/room-types-catalog"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/quote").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/room-type-cards").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/public/bookings").permitAll()
                        // S2-08: khách tra cứu booking bằng mã và email
                        .requestMatchers(HttpMethod.POST, "/api/public/bookings/lookup").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/public/rooms/**").permitAll()

                        // S2-09: Tệp ảnh tĩnh loại phòng công khai
                        .requestMatchers("/uploads/**").permitAll()

                        // API chưa khai báo quyền sẽ bị từ chối
                        .requestMatchers("/api/**").denyAll()

                        .anyRequest().permitAll()
                )
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter()
                                )
                        )
                )
                .addFilterAfter(
                        new AccountStatusFilter(userRepository),
                        BearerTokenAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();

        authoritiesConverter.setAuthoritiesClaimName("role");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter =
                new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        return converter;
    }

    @Bean
    JwtDecoder jwtDecoder(JwtProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(
                        new javax.crypto.spec.SecretKeySpec(
                                properties.getAccessSecret()
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                                "HmacSHA256"
                        )
                )
                .macAlgorithm(
                        org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256
                )
                .build();

        decoder.setJwtValidator(
                new org.springframework.security.oauth2.core
                        .DelegatingOAuth2TokenValidator<>(
                                new JwtTimestampValidator(Duration.ZERO),
                                claimValidator("iss", "homestay"),
                                claimValidator("typ", "access"),
                                jwt -> jwt.getAudience().contains("homestay-api")
                                        ? OAuth2TokenValidatorResult.success()
                                        : OAuth2TokenValidatorResult.failure(
                                                new OAuth2Error(
                                                        "invalid_token",
                                                        "Invalid audience",
                                                        null
                                                )
                                        )
                        )
        );

        return decoder;
    }

    private OAuth2TokenValidator<Jwt> claimValidator(
            String claim,
            String expectedValue
    ) {
        return jwt -> expectedValue.equals(jwt.getClaimAsString(claim))
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error(
                                "invalid_token",
                                "Invalid JWT claim",
                                null
                        )
                );
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173",
                "http://localhost:5174",
                "http://127.0.0.1:5173",
                "http://127.0.0.1:5174"
        ));

        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type"
        ));

        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/api/**", configuration);

        return source;
    }
}
