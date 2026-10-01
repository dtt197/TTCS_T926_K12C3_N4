package com.ttcs.homestay.service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttcs.homestay.dto.user.CreateUserRequest;
import com.ttcs.homestay.dto.user.UpdateUserRequest;
import com.ttcs.homestay.dto.user.UpdateUserStatusRequest;
import com.ttcs.homestay.dto.user.UserResponse;
import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.exception.EmailAlreadyUsedException;
import com.ttcs.homestay.exception.InvalidRoleException;
import com.ttcs.homestay.exception.ResendPasswordNotAllowedException;
import com.ttcs.homestay.exception.SelfDeactivationException;
import com.ttcs.homestay.exception.SelfRoleChangeException;
import com.ttcs.homestay.exception.UserNotFoundException;
import com.ttcs.homestay.repository.RoleRepository;
import com.ttcs.homestay.repository.UserRepository;

@Service
public class UserService {

    /** Bỏ các ký tự dễ nhầm như 0/O, 1/l/I. */
    private static final String PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private static final int TEMP_PASSWORD_LENGTH = 10;

    private final SecureRandom random = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final AuditLogService auditLogService;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            MailService mailService,
            AuditLogService auditLogService) {

        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.mailService = mailService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    /*
     * Giữ method cũ để các test hiện tại vẫn chạy.
     */
    @Transactional
    public UserResponse createUser(
            CreateUserRequest request) {

        return createUser(
                request,
                null,
                null,
                null
        );
    }

    /*
     * Method dùng cho API thật.
     * Có thêm thông tin người thao tác và IP để ghi audit.
     */
    @Transactional
    public UserResponse createUser(
            CreateUserRequest request,
            Long actorUserId,
            String actorEmail,
            String ipAddress) {

        String email =
                request.email()
                        .trim()
                        .toLowerCase(Locale.ROOT);

        // AC3: email trùng → từ chối.
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyUsedException(
                    request.email().trim()
            );
        }

        // AC2: kiểm tra role.
        Role role =
                roleRepository
                        .findByCode(request.role())
                        .orElseThrow(
                                () -> new InvalidRoleException(
                                        request.role()
                                )
                        );

        // AC4: tạo mật khẩu tạm.
        String temporaryPassword =
                generateTemporaryPassword();

        String phone =
                request.phone() == null
                        || request.phone().isBlank()
                        ? null
                        : request.phone().trim();

        boolean active =
                request.active() == null
                        || request.active();

        User user =
                User.createStaff(
                        request.fullName().trim(),
                        email,
                        phone,
                        role,
                        active,
                        passwordEncoder.encode(
                                temporaryPassword
                        )
                );

        User saved =
                userRepository.save(user);

        mailService.sendTemporaryPassword(
                saved,
                temporaryPassword
        );

        /*
         * Chỉ ghi audit khi được gọi từ API thật.
         * Test cũ gọi overload 1 tham số nên không bị phát sinh log mới.
         */
        if (actorUserId != null
                || actorEmail != null
                || ipAddress != null) {

            auditLogService.recordSensitiveAction(
                    actorUserId,
                    actorEmail,
                    saved.getId(),
                    saved.getEmail(),
                    "USER_CREATED",
                    ipAddress
            );
        }

        return UserResponse.from(saved);
    }

    /**
     * S1-02 AC5:
     * vô hiệu hoá / kích hoạt lại.
     */
    @Transactional
    public UserResponse updateStatus(
            Long userId,
            UpdateUserStatusRequest request,
            Long currentAdminId) {

        return updateStatus(
                userId,
                request,
                currentAdminId,
                null,
                null
        );
    }

    @Transactional
    public UserResponse updateStatus(
            Long userId,
            UpdateUserStatusRequest request,
            Long currentAdminId,
            String actorEmail,
            String ipAddress) {

        User user =
                userRepository
                        .findWithRoleById(userId)
                        .orElseThrow(
                                UserNotFoundException::new
                        );

        if (!request.active()
                && userId.equals(currentAdminId)) {

            throw new SelfDeactivationException();
        }

        boolean wasActive =
                user.isActive();

        user.updateActive(
                request.active()
        );

        User saved =
                userRepository.save(user);

        /*
         * Tắt tài khoản.
         */
        if (wasActive
                && !request.active()) {

            auditLogService.recordSensitiveAction(
                    currentAdminId,
                    actorEmail,
                    saved.getId(),
                    saved.getEmail(),
                    "ACCOUNT_DISABLED",
                    ipAddress
            );
        }

        /*
         * Kích hoạt lại tài khoản.
         */
        else if (!wasActive
                && request.active()) {

            auditLogService.recordSensitiveAction(
                    currentAdminId,
                    actorEmail,
                    saved.getId(),
                    saved.getEmail(),
                    "ACCOUNT_ENABLED",
                    ipAddress
            );
        }

        return UserResponse.from(saved);
    }

    /**
     * Lát 4:
     * sửa họ tên, số điện thoại, vai trò.
     */
    @Transactional
    public UserResponse updateUser(
            Long userId,
            UpdateUserRequest request,
            Long currentAdminId) {

        return updateUser(
                userId,
                request,
                currentAdminId,
                null,
                null
        );
    }

    @Transactional
    public UserResponse updateUser(
            Long userId,
            UpdateUserRequest request,
            Long currentAdminId,
            String actorEmail,
            String ipAddress) {

        User user =
                userRepository
                        .findWithRoleById(userId)
                        .orElseThrow(
                                UserNotFoundException::new
                        );

        String previousRole =
                user.getRole().getCode();

        Role role =
                roleRepository
                        .findByCode(request.role())
                        .orElseThrow(
                                () -> new InvalidRoleException(
                                        request.role()
                                )
                        );

        /*
         * Admin không được tự đổi role của mình.
         */
        if (userId.equals(currentAdminId)
                && !user.getRole()
                        .getCode()
                        .equals(role.getCode())) {

            throw new SelfRoleChangeException();
        }

        String phone =
                request.phone() == null
                        || request.phone().isBlank()
                        ? null
                        : request.phone().trim();

        user.updateProfile(
                request.fullName().trim(),
                phone,
                role
        );

        User saved =
                userRepository.save(user);

        boolean roleChanged =
                !Objects.equals(
                        previousRole,
                        role.getCode()
                );

        /*
         * Nếu có đổi role thì ghi đúng 1 log ROLE_CHANGED.
         */
        if (roleChanged) {

            auditLogService.recordSensitiveAction(
                    currentAdminId,
                    actorEmail,
                    saved.getId(),
                    saved.getEmail(),
                    "ROLE_CHANGED",
                    ipAddress
            );
        }

        /*
         * Nếu không đổi role mà chỉ sửa họ tên / số điện thoại
         * thì ghi USER_UPDATED.
         */
        else {

            auditLogService.recordSensitiveAction(
                    currentAdminId,
                    actorEmail,
                    saved.getId(),
                    saved.getEmail(),
                    "USER_UPDATED",
                    ipAddress
            );
        }

        return UserResponse.from(saved);
    }

    /**
     * Lát 4:
     * gửi lại mật khẩu tạm.
     *
     * Giữ method cũ để test hiện tại vẫn chạy.
     */
    @Transactional
    public UserResponse resendTemporaryPassword(
            Long userId) {

        return resendTemporaryPassword(
                userId,
                null,
                null,
                null
        );
    }

    /**
     * Method dùng cho API thật.
     */
    @Transactional
    public UserResponse resendTemporaryPassword(
            Long userId,
            Long actorUserId,
            String actorEmail,
            String ipAddress) {

        User user =
                userRepository
                        .findWithRoleById(userId)
                        .orElseThrow(
                                UserNotFoundException::new
                        );

        if (!user.isMustChangePassword()) {
            throw new ResendPasswordNotAllowedException();
        }

        String temporaryPassword =
                generateTemporaryPassword();

        user.resetTemporaryPassword(
                passwordEncoder.encode(
                        temporaryPassword
                )
        );

        User saved =
                userRepository.save(user);

        mailService.sendTemporaryPassword(
                saved,
                temporaryPassword
        );

        /*
         * Chỉ API thật mới ghi audit.
         */
        if (actorUserId != null
                || actorEmail != null
                || ipAddress != null) {

            auditLogService.recordSensitiveAction(
                    actorUserId,
                    actorEmail,
                    saved.getId(),
                    saved.getEmail(),
                    "TEMP_PASSWORD_RESENT",
                    ipAddress
            );
        }

        return UserResponse.from(saved);
    }

    /**
     * 10 ký tự, luôn có cả chữ và số.
     */
    String generateTemporaryPassword() {

        while (true) {

            StringBuilder sb =
                    new StringBuilder(
                            TEMP_PASSWORD_LENGTH
                    );

            for (int i = 0;
                    i < TEMP_PASSWORD_LENGTH;
                    i++) {

                sb.append(
                        PASSWORD_CHARS.charAt(
                                random.nextInt(
                                        PASSWORD_CHARS.length()
                                )
                        )
                );
            }

            String password =
                    sb.toString();

            if (password
                    .chars()
                    .anyMatch(
                            Character::isDigit
                    )
                    &&
                    password
                            .chars()
                            .anyMatch(
                                    Character::isLetter
                            )) {

                return password;
            }
        }
    }
}