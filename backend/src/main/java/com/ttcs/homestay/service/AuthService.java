package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.auth.LoginRequest;
import com.ttcs.homestay.dto.auth.LoginResponse;
import com.ttcs.homestay.dto.auth.LoginResult;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.exception.InvalidCredentialsException;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.security.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtTokenService jwtTokenService;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtTokenService jwtTokenService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtTokenService = jwtTokenService;
	}

	@Transactional
	public LoginResult login(LoginRequest request) {
		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
		User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
				.orElseThrow(InvalidCredentialsException::new);
		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

		if (!user.isActive() || user.isLockedAt(now)) {
			throw new InvalidCredentialsException();
		}

		if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
			user.recordFailedLogin(now);
			userRepository.save(user);
			throw new InvalidCredentialsException();
		}

		user.resetLoginFailures();
		userRepository.save(user);

		JwtTokenService.IssuedTokens issuedTokens = jwtTokenService.issueTokens(user);
		LoginResponse response = new LoginResponse(
				user.getId(),
				user.getFullName(),
				user.getEmail(),
				user.getRole().getCode(),
				issuedTokens.accessToken(),
				jwtTokenService.accessTtlSeconds(),
				user.isMustChangePassword()
		);
		return new LoginResult(response, issuedTokens.refreshToken());
	}
}