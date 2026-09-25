package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.auth.LoginRequest;
import com.ttcs.homestay.dto.auth.LoginResponse;
import com.ttcs.homestay.dto.auth.LoginResult;
import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.exception.InvalidCredentialsException;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.security.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PasswordEncoder passwordEncoder;

	@Mock
	private JwtTokenService jwtTokenService;

	@InjectMocks
	private AuthService authService;

	@Test
	void loginReturnsSafeResponseForValidCredentials() {
		User user = mock(User.class);
		Role role = mock(Role.class);
		when(userRepository.findByEmailIgnoreCase(eq("admin.demo@homestay.local"))).thenReturn(Optional.of(user));
		when(user.isActive()).thenReturn(true);
		when(user.getPasswordHash()).thenReturn("$2a$10$demo-hash");
		when(passwordEncoder.matches("password", "$2a$10$demo-hash")).thenReturn(true);
		when(user.getRole()).thenReturn(role);
		when(user.getId()).thenReturn(1L);
		when(user.getFullName()).thenReturn("Demo Administrator");
		when(user.getEmail()).thenReturn("admin.demo@homestay.local");
		when(role.getCode()).thenReturn("ADMIN");
		when(jwtTokenService.issueTokens(user)).thenReturn(
				new JwtTokenService.IssuedTokens("access-token", "refresh-token"));
		when(jwtTokenService.accessTtlSeconds()).thenReturn(1800L);

		LoginResult result = authService.login(new LoginRequest("  ADMIN.DEMO@HOMESTAY.LOCAL ", "password"));
		LoginResponse response = result.response();

		assertThat(response.email()).isEqualTo("admin.demo@homestay.local");
		assertThat(response.role()).isEqualTo("ADMIN");
		assertThat(response.accessToken()).isEqualTo("access-token");
		assertThat(response.expiresIn()).isEqualTo(1800L);
		assertThat(result.refreshToken()).isEqualTo("refresh-token");
		assertThat(response).extracting("userId", "fullName", "email", "role")
				.containsExactly(1L, "Demo Administrator", "admin.demo@homestay.local", "ADMIN");
		verify(user).resetLoginFailures();
		verify(userRepository).save(user);
	}

	@Test
	void loginUsesSameErrorForUnknownEmailAndWrongPassword() {
		when(userRepository.findByEmailIgnoreCase("missing@homestay.local")).thenReturn(Optional.empty());

		InvalidCredentialsException missingEmail = catchInvalidCredentials(
				new LoginRequest("missing@homestay.local", "password"));

		User user = mock(User.class);
		when(userRepository.findByEmailIgnoreCase("admin.demo@homestay.local")).thenReturn(Optional.of(user));
		when(user.isActive()).thenReturn(true);
		when(user.getPasswordHash()).thenReturn("$2a$10$demo-hash");
		when(passwordEncoder.matches("wrong-password", "$2a$10$demo-hash")).thenReturn(false);

		InvalidCredentialsException wrongPassword = catchInvalidCredentials(
				new LoginRequest("admin.demo@homestay.local", "wrong-password"));

		assertThat(missingEmail.getMessage()).isEqualTo(wrongPassword.getMessage());
		verify(user).recordFailedLogin(org.mockito.ArgumentMatchers.any());
		verify(userRepository).save(user);
		verifyNoTokenIssued();
	}

	@Test
	void lockedAccountReturnsGenericErrorWithoutCheckingPasswordOrIssuingToken() {
		User user = mock(User.class);
		when(userRepository.findByEmailIgnoreCase("admin.demo@homestay.local")).thenReturn(Optional.of(user));
		when(user.isActive()).thenReturn(true);
		when(user.isLockedAt(org.mockito.ArgumentMatchers.any())).thenReturn(true);

		InvalidCredentialsException exception = catchInvalidCredentials(
				new LoginRequest("admin.demo@homestay.local", "password"));

		assertThat(exception.getMessage()).isEqualTo("Email hoặc mật khẩu không chính xác");
		verify(passwordEncoder, never()).matches(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
		verifyNoTokenIssued();
	}

	@Test
	void passwordEncoderMatchesBcryptHash() {
		BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
		String hash = passwordEncoder.encode("password");

		assertThat(hash).startsWith("$2");
		assertThat(passwordEncoder.matches("password", hash)).isTrue();
		assertThat(passwordEncoder.matches("wrong-password", hash)).isFalse();
	}

	private InvalidCredentialsException catchInvalidCredentials(LoginRequest request) {
		return (InvalidCredentialsException) assertThatThrownBy(() -> authService.login(request))
				.isInstanceOf(InvalidCredentialsException.class)
				.actual();
	}

	private void verifyNoTokenIssued() {
		verify(jwtTokenService, never()).issueTokens(org.mockito.ArgumentMatchers.any());
	}
}