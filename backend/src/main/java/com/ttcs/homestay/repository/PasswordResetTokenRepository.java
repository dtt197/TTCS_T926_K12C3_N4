package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.PasswordResetToken;
import com.ttcs.homestay.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

	Optional<PasswordResetToken> findByTokenHash(String tokenHash);

	long countByUserAndCreatedAtAfter(User user, OffsetDateTime after);

	List<PasswordResetToken> findAllByUserAndUsedAtIsNull(User user);
}