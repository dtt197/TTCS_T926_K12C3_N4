package com.ttcs.homestay.service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttcs.homestay.dto.user.CreateUserRequest;
import com.ttcs.homestay.dto.user.UserResponse;
import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.exception.EmailAlreadyUsedException;
import com.ttcs.homestay.exception.InvalidRoleException;
import com.ttcs.homestay.repository.RoleRepository;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.exception.SelfDeactivationException;
import com.ttcs.homestay.exception.UserNotFoundException;
import com.ttcs.homestay.dto.user.UpdateUserStatusRequest;
import com.ttcs.homestay.dto.user.UpdateUserRequest;
import com.ttcs.homestay.exception.ResendPasswordNotAllowedException;
import com.ttcs.homestay.exception.SelfRoleChangeException;

@Service
public class UserService {

	/** Bỏ các ký tự dễ nhầm như 0/O, 1/l/I. */
	private static final String PASSWORD_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
	private static final int TEMP_PASSWORD_LENGTH = 10;

	private final SecureRandom random = new SecureRandom();
	private final UserRepository userRepository;
	private final RoleRepository roleRepository;
	private final PasswordEncoder passwordEncoder;
	private final MailService mailService;

	public UserService(UserRepository userRepository, RoleRepository roleRepository,
			PasswordEncoder passwordEncoder, MailService mailService) {
		this.userRepository = userRepository;
		this.roleRepository = roleRepository;
		this.passwordEncoder = passwordEncoder;
		this.mailService = mailService;
	}

	@Transactional(readOnly = true)
	public List<UserResponse> listUsers() {
		return userRepository.findAllByOrderByCreatedAtDesc().stream()
				.map(UserResponse::from)
				.toList();
	}

	@Transactional
	public UserResponse createUser(CreateUserRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);

		// AC3: email trùng (không phân biệt hoa thường) → từ chối, nêu rõ email
		if (userRepository.existsByEmailIgnoreCase(email)) {
			throw new EmailAlreadyUsedException(request.email().trim());
		}

		// AC2: vai trò phải là một trong 4 vai trò có trong bảng roles
		Role role = roleRepository.findByCode(request.role())
				.orElseThrow(() -> new InvalidRoleException(request.role()));

		// AC4: mật khẩu tạm lưu dạng mã hoá BCrypt, tài khoản bắt buộc đổi ở lần đăng nhập đầu
		String temporaryPassword = generateTemporaryPassword();
		String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
		boolean active = request.active() == null || request.active();

		User user = User.createStaff(request.fullName().trim(), email, phone, role, active,
				passwordEncoder.encode(temporaryPassword));
		User saved = userRepository.save(user);

		mailService.sendTemporaryPassword(saved, temporaryPassword);
		return UserResponse.from(saved);
	}
	/** S1-02 AC5: vô hiệu hoá / kích hoạt lại. Phiên đang dùng bị chặn ngay bởi AccountStatusFilter. */
	@Transactional
	public UserResponse updateStatus(Long userId, UpdateUserStatusRequest request, Long currentAdminId) {
		User user = userRepository.findWithRoleById(userId)
				.orElseThrow(UserNotFoundException::new);
		if (!request.active() && userId.equals(currentAdminId)) {
			throw new SelfDeactivationException();
		}
		user.updateActive(request.active());
		return UserResponse.from(userRepository.save(user));
	}
	/** Lát 4: sửa họ tên, số điện thoại, vai trò. Đổi vai trò → AccountStatusFilter bắt đăng nhập lại. */
	@Transactional
	public UserResponse updateUser(Long userId, UpdateUserRequest request, Long currentAdminId) {
		User user = userRepository.findWithRoleById(userId)
				.orElseThrow(UserNotFoundException::new);
		Role role = roleRepository.findByCode(request.role())
				.orElseThrow(() -> new InvalidRoleException(request.role()));
		if (userId.equals(currentAdminId) && !user.getRole().getCode().equals(role.getCode())) {
			throw new SelfRoleChangeException();
		}
		String phone = request.phone() == null || request.phone().isBlank() ? null : request.phone().trim();
		user.updateProfile(request.fullName().trim(), phone, role);
		return UserResponse.from(userRepository.save(user));
	}

	/** Lát 4: gửi lại mật khẩu tạm mới (vd email lần trước không tới). Mật khẩu tạm cũ hết dùng được. */
	@Transactional
	public UserResponse resendTemporaryPassword(Long userId) {
		User user = userRepository.findWithRoleById(userId)
				.orElseThrow(UserNotFoundException::new);
		if (!user.isMustChangePassword()) {
			throw new ResendPasswordNotAllowedException();
		}
		String temporaryPassword = generateTemporaryPassword();
		user.resetTemporaryPassword(passwordEncoder.encode(temporaryPassword));
		User saved = userRepository.save(user);
		mailService.sendTemporaryPassword(saved, temporaryPassword);
		return UserResponse.from(saved);
	}
	/** 10 ký tự, luôn có cả chữ và số. */
	String generateTemporaryPassword() {
		while (true) {
			StringBuilder sb = new StringBuilder(TEMP_PASSWORD_LENGTH);
			for (int i = 0; i < TEMP_PASSWORD_LENGTH; i++) {
				sb.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
			}
			String password = sb.toString();
			if (password.chars().anyMatch(Character::isDigit) && password.chars().anyMatch(Character::isLetter)) {
				return password;
			}
		}
	}
}