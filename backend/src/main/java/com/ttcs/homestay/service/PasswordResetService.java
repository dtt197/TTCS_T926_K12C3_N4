package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.auth.ForgotPasswordRequest;
import com.ttcs.homestay.dto.auth.ResetPasswordRequest;
import com.ttcs.homestay.entity.PasswordResetToken;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.exception.InvalidResetTokenException;
import com.ttcs.homestay.exception.TooManyResetRequestsException;
import com.ttcs.homestay.repository.PasswordResetTokenRepository;
import com.ttcs.homestay.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Locale;

/** S1-03 (SCRUM-15, 22, 27): quên mật khẩu qua email, giới hạn số lần yêu cầu, huỷ token/phiên cũ. */
@Service
public class PasswordResetService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private final UserRepository userRepository;
	private final PasswordResetTokenRepository tokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final MailService mailService;
	private final Duration tokenTtl;
	private final int maxRequestsPerWindow;
	private final Duration requestWindow;

	public PasswordResetService(
			UserRepository userRepository,
			PasswordResetTokenRepository tokenRepository,
			PasswordEncoder passwordEncoder,
			MailService mailService,
			@Value("${app.password-reset.token-ttl:PT30M}") Duration tokenTtl,
			@Value("${app.password-reset.max-requests:3}") int maxRequestsPerWindow,
			@Value("${app.password-reset.request-window:PT1H}") Duration requestWindow) {
		this.userRepository = userRepository;
		this.tokenRepository = tokenRepository;
		this.passwordEncoder = passwordEncoder;
		this.mailService = mailService;
		this.tokenTtl = tokenTtl;
		this.maxRequestsPerWindow = maxRequestsPerWindow;
		this.requestWindow = requestWindow;
	}

	/** SCRUM-15 + SCRUM-22: không tiết lộ email có tồn tại hay không, luôn coi như thành công với người gọi. */
	@Transactional
	public void requestReset(ForgotPasswordRequest request) {
		String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
		userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
			OffsetDateTime now = OffsetDateTime.now();
			long recentRequests = tokenRepository.countByUserAndCreatedAtAfter(user, now.minus(requestWindow));
			if (recentRequests >= maxRequestsPerWindow) {
				throw new TooManyResetRequestsException();
			}

			// SCRUM-27: huỷ mọi token cũ chưa dùng trước khi phát token mới
			tokenRepository.findAllByUserAndUsedAtIsNull(user).forEach(PasswordResetToken::markUsed);

			String rawToken = generateRawToken();
			PasswordResetToken token = PasswordResetToken.issue(user, hash(rawToken), now.plus(tokenTtl));
			tokenRepository.save(token);

			mailService.sendPasswordResetLink(user, rawToken);
		});
	}

	@Transactional
	public void resetPassword(ResetPasswordRequest request) {
		PasswordResetToken token = tokenRepository.findByTokenHash(hash(request.token()))
				.orElseThrow(InvalidResetTokenException::new);

		if (!token.isUsable(OffsetDateTime.now())) {
			throw new InvalidResetTokenException();
		}

		User user = token.getUser();
		user.changePassword(passwordEncoder.encode(request.newPassword()));
		userRepository.save(user);

		token.markUsed();
		// SCRUM-27: các token đặt-lại-mật-khẩu khác đang chờ cũng huỷ theo, tránh dùng lại sau khi đã đổi
		tokenRepository.findAllByUserAndUsedAtIsNull(user).forEach(PasswordResetToken::markUsed);
	}

	private String generateRawToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return Base64.getEncoder().encodeToString(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 không khả dụng", e);
		}
	}
}