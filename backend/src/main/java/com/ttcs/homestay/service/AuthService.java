package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.auth.LoginRequest;
import com.ttcs.homestay.dto.auth.LoginResponse;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.exception.InvalidCredentialsException;
import com.ttcs.homestay.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public LoginResponse login(LoginRequest request) {
		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
		User user = userRepository.findByEmailIgnoreCase(normalizedEmail)
				.filter(User::isActive)
				.filter(foundUser -> passwordEncoder.matches(request.password(), foundUser.getPasswordHash()))
				.orElseThrow(InvalidCredentialsException::new);

		return new LoginResponse(
				user.getId(),
				user.getFullName(),
				user.getEmail(),
				user.getRole().getCode()
		);
	}
}