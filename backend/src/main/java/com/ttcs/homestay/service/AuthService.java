package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.auth.LoginRequest;
import com.ttcs.homestay.dto.auth.LoginResponse;
import com.ttcs.homestay.dto.auth.LoginResult;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.exception.InvalidCredentialsException;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.security.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

	public LoginResult login(LoginRequest request) {
		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
		User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
				.filter(User::isActive)
				.filter(foundUser -> passwordEncoder.matches(request.password(), foundUser.getPasswordHash()))
				.orElseThrow(InvalidCredentialsException::new);

		JwtTokenService.IssuedTokens issuedTokens = jwtTokenService.issueTokens(user);
		LoginResponse response = new LoginResponse(
				user.getId(),
				user.getFullName(),
				user.getEmail(),
				user.getRole().getCode(),
				issuedTokens.accessToken(),
				jwtTokenService.accessTtlSeconds()
		);
		return new LoginResult(response, issuedTokens.refreshToken());
	}
}