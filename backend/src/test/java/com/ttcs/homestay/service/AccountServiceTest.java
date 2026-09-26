package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ttcs.homestay.dto.account.ChangePasswordRequest;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.exception.PasswordChangeException;
import com.ttcs.homestay.repository.UserRepository;

/** S1-02 Lát 2: đổi mật khẩu tạm. */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

	@Mock
	UserRepository userRepository;

	@Mock
	PasswordEncoder passwordEncoder;

	@InjectMocks
	AccountService accountService;

	@Test
	void matKhauHienTaiSai_biTuChoi() {
		User user = mock(User.class);
		when(user.getPasswordHash()).thenReturn("HASH_CU");
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("sai-mat-khau", "HASH_CU")).thenReturn(false);

		assertThatThrownBy(() -> accountService.changePassword(1L, new ChangePasswordRequest("sai-mat-khau", "MatKhauMoi9")))
				.isInstanceOf(PasswordChangeException.class)
				.hasMessage("Mật khẩu hiện tại không đúng");
		verify(user, never()).changePassword(any());
	}

	@Test
	void matKhauMoiTrungMatKhauCu_biTuChoi() {
		User user = mock(User.class);
		when(user.getPasswordHash()).thenReturn("HASH_CU");
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("MatKhauTam1", "HASH_CU")).thenReturn(true);

		assertThatThrownBy(() -> accountService.changePassword(1L, new ChangePasswordRequest("MatKhauTam1", "MatKhauTam1")))
				.isInstanceOf(PasswordChangeException.class)
				.hasMessage("Mật khẩu mới phải khác mật khẩu hiện tại");
	}

	@Test
	void doiThanhCong_luuMatKhauMoiDaMaHoa() {
		User user = mock(User.class);
		when(user.getPasswordHash()).thenReturn("HASH_CU");
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("MatKhauTam1", "HASH_CU")).thenReturn(true);
		when(passwordEncoder.matches("MatKhauMoi9", "HASH_CU")).thenReturn(false);
		when(passwordEncoder.encode("MatKhauMoi9")).thenReturn("HASH_MOI");

		accountService.changePassword(1L, new ChangePasswordRequest("MatKhauTam1", "MatKhauMoi9"));

		verify(user).changePassword("HASH_MOI");
		verify(userRepository).save(user);
	}
}