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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.Locale;

@Entity
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "full_name", nullable = false, length = 150)
	private String fullName;

	@Column(nullable = false, length = 255)
	private String email;

	@Column(length = 30)
	private String phone;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "role_id", nullable = false)
	private Role role;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "failed_login_count", nullable = false)
	private int failedLoginCount;

	@Column(name = "first_failed_login_at")
	private LocalDateTime firstFailedLoginAt;

	@Column(name = "locked_until")
	private LocalDateTime lockedUntil;

	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected User() {
	}

	@PrePersist
	void onCreate() {
		normalizeEmail();
		OffsetDateTime now = OffsetDateTime.now();
		createdAt = now;
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		normalizeEmail();
		updatedAt = OffsetDateTime.now();
	}

	private void normalizeEmail() {
		if (email != null) {
			email = email.trim().toLowerCase(Locale.ROOT);
		}
	}

	public Long getId() {
		return id;
	}

	public String getFullName() {
		return fullName;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public Role getRole() {
		return role;
	}

	public boolean isActive() {
		return active;
	}

	public boolean isLockedAt(LocalDateTime now) {
		return lockedUntil != null && now.isBefore(lockedUntil);
	}

	public void recordFailedLogin(LocalDateTime now) {
		if (firstFailedLoginAt == null || !now.isBefore(firstFailedLoginAt.plusMinutes(30))) {
			failedLoginCount = 0;
			firstFailedLoginAt = now;
			lockedUntil = null;
		}

		failedLoginCount++;
		if (failedLoginCount >= 5) {
			lockedUntil = now.plusMinutes(30);
		}
	}

	public void resetLoginFailures() {
		failedLoginCount = 0;
		firstFailedLoginAt = null;
		lockedUntil = null;
	}

	public int getFailedLoginCount() {
		return failedLoginCount;
	}

	public LocalDateTime getFirstFailedLoginAt() {
		return firstFailedLoginAt;
	}

	public LocalDateTime getLockedUntil() {
		return lockedUntil;
	}
}