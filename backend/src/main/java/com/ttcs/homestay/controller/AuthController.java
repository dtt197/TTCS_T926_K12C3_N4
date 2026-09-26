package com.ttcs.homestay.controller;

import com.ttcs.homestay.dto.auth.LoginRequest;
import com.ttcs.homestay.dto.auth.LoginResult;
import com.ttcs.homestay.dto.auth.RefreshResponse;
import com.ttcs.homestay.config.JwtProperties;
import com.ttcs.homestay.exception.InvalidRefreshTokenException;
import com.ttcs.homestay.security.JwtTokenService;
import com.ttcs.homestay.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CookieValue;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final JwtTokenService jwtTokenService;
	private final JwtProperties jwtProperties;

	public AuthController(AuthService authService, JwtTokenService jwtTokenService, JwtProperties jwtProperties) {
		this.authService = authService;
		this.jwtTokenService = jwtTokenService;
		this.jwtProperties = jwtProperties;
	}

	@PostMapping("/login")
	public ResponseEntity<com.ttcs.homestay.dto.auth.LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		LoginResult result = authService.login(request);
		return ResponseEntity.ok()
				.header("Set-Cookie", refreshCookie(result.refreshToken()).toString())
				.body(result.response());
	}


	@PostMapping("/refresh")
	public ResponseEntity<RefreshResponse> refresh(
				@CookieValue(name = "${app.jwt.refresh-cookie-name}", required = false) String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new InvalidRefreshTokenException();
		}
		return ResponseEntity.ok(jwtTokenService.refreshAccessToken(refreshToken));
				}
@PostMapping("/logout")
public ResponseEntity<Void> logout() {
    return ResponseEntity.noContent()
            .header("Set-Cookie", clearRefreshCookie().toString())
            .build();
		}
	



	private ResponseCookie refreshCookie(String token) {
		return ResponseCookie.from(jwtProperties.getRefreshCookieName(), token)
				.httpOnly(true)
				.secure(jwtProperties.isRefreshCookieSecure())
				.sameSite("Lax")
				.path("/api/auth")
				.maxAge(jwtProperties.getRefreshTtl())
				.build();
	}
	private ResponseCookie clearRefreshCookie() {
    return ResponseCookie.from(jwtProperties.getRefreshCookieName(), "")
            .httpOnly(true)
            .secure(jwtProperties.isRefreshCookieSecure())
            .sameSite("Lax")
            .path("/api/auth")
            .maxAge(0)
            .build();
}
}