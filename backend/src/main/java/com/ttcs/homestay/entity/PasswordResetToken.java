package com.ttcs.homestay.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(name = "token_hash", nullable = false, length = 255, unique = true)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private OffsetDateTime expiresAt;

	@Column(name = "used_at")
	private OffsetDateTime usedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected PasswordResetToken() {
	}

	public static PasswordResetToken issue(User user, String tokenHash, OffsetDateTime expiresAt) {
		PasswordResetToken token = new PasswordResetToken();
		token.user = user;
		token.tokenHash = tokenHash;
		token.expiresAt = expiresAt;
		return token;
	}

	@PrePersist
	void onCreate() {
		createdAt = OffsetDateTime.now();
	}

	public boolean isUsable(OffsetDateTime now) {
		return usedAt == null && now.isBefore(expiresAt);
	}

	public void markUsed() {
		this.usedAt = OffsetDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public OffsetDateTime getExpiresAt() {
		return expiresAt;
	}

	public OffsetDateTime getUsedAt() {
		return usedAt;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}
}