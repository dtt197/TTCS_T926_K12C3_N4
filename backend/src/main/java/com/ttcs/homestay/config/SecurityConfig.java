package com.ttcs.homestay.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.time.Duration;

@Configuration
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers("/api/auth/login").permitAll()
						.requestMatchers("/api/auth/refresh").permitAll()
						.requestMatchers("/api/internal/**").authenticated()
						.anyRequest().permitAll())
				.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
		return http.build();
	}

	@Bean
	JwtDecoder jwtDecoder(JwtProperties properties) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(
				new javax.crypto.spec.SecretKeySpec(properties.getAccessSecret().getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"))
				.macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256)
				.build();
			decoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
					new JwtTimestampValidator(Duration.ZERO),
					claimValidator("iss", "homestay"),
					claimValidator("typ", "access"),
					jwt -> jwt.getAudience().contains("homestay-api")
							? OAuth2TokenValidatorResult.success()
							: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null))));
		return decoder;
	}

	private OAuth2TokenValidator<Jwt> claimValidator(String claim, String expectedValue) {
		return jwt -> expectedValue.equals(jwt.getClaimAsString(claim))
				? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid JWT claim", null));
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:5174"));
		configuration.setAllowedMethods(List.of("POST", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("Content-Type"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/api/**", configuration);
		return source;
	}
}