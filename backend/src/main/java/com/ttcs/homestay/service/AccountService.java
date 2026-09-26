package com.ttcs.homestay.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttcs.homestay.dto.account.ChangePasswordRequest;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.exception.PasswordChangeException;
import com.ttcs.homestay.repository.UserRepository;

/** Người dùng tự đổi mật khẩu của mình (S1-02 Lát 2, dùng lại cho S1-03). */
@Service
public class AccountService {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public void changePassword(Long userId, ChangePasswordRequest request) {
		User user = userRepository.findById(userId)
				.orElseThrow(() -> new PasswordChangeException("Không tìm thấy tài khoản"));
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new PasswordChangeException("Mật khẩu hiện tại không đúng");
		}
		if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			throw new PasswordChangeException("Mật khẩu mới phải khác mật khẩu hiện tại");
		}
		user.changePassword(passwordEncoder.encode(request.newPassword()));
		userRepository.save(user);
	}
}