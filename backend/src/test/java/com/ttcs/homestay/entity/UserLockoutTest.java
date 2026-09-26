package com.ttcs.homestay.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class UserLockoutTest {

	@Test
	void locksAfterFiveFailuresWithinFifteenMinutes(){
		User user = new User();
		LocalDateTime firstFailure = LocalDateTime.of(2026, 9, 25, 10, 0);

		for (int attempt = 0; attempt < 5; attempt++) {
			user.recordFailedLogin(firstFailure.plusMinutes(attempt));
		}

		assertThat(user.getFailedLoginCount()).isEqualTo(5);
		assertThat(user.getFirstFailedLoginAt()).isEqualTo(firstFailure);
		assertThat(user.getLockedUntil()).isEqualTo(firstFailure.plusMinutes(34));
		assertThat(user.isLockedAt(firstFailure.plusMinutes(1))).isTrue();
	}

	@Test
	void resetsFailureWindowAfterFifteenMinutes(){
		User user = new User();
		LocalDateTime firstFailure = LocalDateTime.of(2026, 9, 25, 10, 0);

		user.recordFailedLogin(firstFailure);
		user.recordFailedLogin(firstFailure.plusMinutes(16));

		assertThat(user.getFailedLoginCount()).isEqualTo(1);
		assertThat(user.getFirstFailedLoginAt()).isEqualTo(firstFailure.plusMinutes(16));
		assertThat(user.getLockedUntil()).isNull();
	}

	@Test
	void successfulLoginResetClearsLockoutState() {
		User user = new User();
		LocalDateTime firstFailure = LocalDateTime.of(2026, 9, 25, 10, 0);

		for (int attempt = 0; attempt < 5; attempt++) {
			user.recordFailedLogin(firstFailure.plusMinutes(attempt));
		}

		user.resetLoginFailures();

		assertThat(user.getFailedLoginCount()).isZero();
		assertThat(user.getFirstFailedLoginAt()).isNull();
		assertThat(user.getLockedUntil()).isNull();
	}
}