package com.ttcs.homestay.controller;

import com.ttcs.homestay.config.JwtProperties;
import com.ttcs.homestay.dto.auth.LoginResponse;
import com.ttcs.homestay.dto.auth.LoginResult;
import com.ttcs.homestay.exception.GlobalExceptionHandler;
import com.ttcs.homestay.exception.InvalidCredentialsException;
import com.ttcs.homestay.security.JwtTokenService;
import com.ttcs.homestay.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private AuthService authService;

	@MockitoBean
	private JwtTokenService jwtTokenService;

	@MockitoBean
	private JwtProperties jwtProperties;

	@Test
	void loginReturnsOneRoleWithoutPasswordFields() throws Exception {
		when(jwtProperties.getRefreshCookieName()).thenReturn("refresh_token");
		when(jwtProperties.getRefreshTtl()).thenReturn(Duration.ofDays(7));
		when(jwtProperties.isRefreshCookieSecure()).thenReturn(false);

		LoginResponse response = new LoginResponse(
				1L,
				"Demo Administrator",
				"admin.demo@homestay.local",
				"ADMIN",
				"access-token",
				1800L);
		when(authService.login(any())).thenReturn(new LoginResult(response, "refresh-token"));

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"admin.demo@homestay.local\",\"password\":\"password\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.role").value("ADMIN"))
				.andExpect(jsonPath("$.roles").doesNotExist())
				.andExpect(jsonPath("$.password").doesNotExist())
				.andExpect(jsonPath("$.passwordHash").doesNotExist());
	}

	@Test
	void loginReturnsGenericUnauthorizedError() throws Exception {
		when(authService.login(any())).thenThrow(new InvalidCredentialsException());

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"missing@homestay.local\",\"password\":\"wrong\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().json("{\"code\":\"INVALID_CREDENTIALS\",\"message\":\"Email hoặc mật khẩu không chính xác\"}"));
	}

	@Test
	void loginRejectsBlankCredentials() throws Exception {
		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"\",\"password\":\"\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

}