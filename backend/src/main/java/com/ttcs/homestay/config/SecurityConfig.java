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
import com.ttcs.homestay.security.ApiAccessDeniedHandler;
import com.ttcs.homestay.security.ApiAuthenticationEntryPoint;
import com.ttcs.homestay.security.ApiSecurityPolicyCatalog;

@Configuration
public class SecurityConfig {

    @Bean
    ApiAuthenticationEntryPoint apiAuthenticationEntryPoint() {
        return new ApiAuthenticationEntryPoint();
    }

    @Bean
    ApiAccessDeniedHandler apiAccessDeniedHandler() {
        return new ApiAccessDeniedHandler();
    }

    @Bean
    ApiSecurityPolicyCatalog apiSecurityPolicyCatalog() {
        return new ApiSecurityPolicyCatalog();
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            UserRepository userRepository,
            ApiAuthenticationEntryPoint authenticationEntryPoint,
            ApiAccessDeniedHandler accessDeniedHandler,
            ApiSecurityPolicyCatalog policyCatalog
    ) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> {
                    for (ApiSecurityPolicyCatalog.Policy policy : policyCatalog.policies()) {
                        var request = policy.method() == null
                                ? authorize.requestMatchers(policy.path())
                                : authorize.requestMatchers(
                                        HttpMethod.valueOf(policy.method()),
                                        policy.path()
                                );

                        switch (policy.access()) {
                            case PUBLIC -> request.permitAll();
                            case AUTHENTICATED -> request.authenticated();
                            case ROLES -> request.hasAnyRole(
                                    policy.roles().toArray(String[]::new)
                            );
                            case DENIED -> request.denyAll();
                        }
                    }
                    authorize.anyRequest().permitAll();
                })
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .oauth2ResourceServer(oauth2 ->
                        oauth2.jwt(jwt ->
                                jwt.jwtAuthenticationConverter(
                                        jwtAuthenticationConverter()
                                )
                        ).authenticationEntryPoint(authenticationEntryPoint)
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
