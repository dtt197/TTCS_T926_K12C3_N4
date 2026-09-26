package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.ttcs.homestay.dto.user.CreateUserRequest;
import com.ttcs.homestay.dto.user.UserResponse;
import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.exception.EmailAlreadyUsedException;
import com.ttcs.homestay.exception.InvalidRoleException;
import com.ttcs.homestay.repository.RoleRepository;
import com.ttcs.homestay.repository.UserRepository;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** S1-02 Lát 1: tạo tài khoản nhân viên. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	UserRepository userRepository;

	@Mock
	RoleRepository roleRepository;

	@Mock
	PasswordEncoder passwordEncoder;

	@Mock
	MailService mailService;

	@InjectMocks
	UserService userService;

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	private CreateUserRequest request(String email, String role) {
		return new CreateUserRequest("Nguyễn Văn An", email, "0912345678", role, true);
	}

	@Test
	void emailTrungKhacHoaThuong_biTuChoi_vaThongBaoNeuRoEmail() {
		when(userRepository.existsByEmailIgnoreCase("letan@homestay.local")).thenReturn(true);

		assertThatThrownBy(() -> userService.createUser(request("LeTan@Homestay.local", "RECEPTIONIST")))
				.isInstanceOf(EmailAlreadyUsedException.class)
				.hasMessageContaining("LeTan@Homestay.local");

		verify(userRepository, never()).save(any());
		verify(mailService, never()).sendTemporaryPassword(any(), anyString());
	}

	@Test
	void vaiTroKhongThuocBonVaiTro_biTuChoi() {
		when(roleRepository.findByCode("GIAM_DOC")).thenReturn(Optional.empty());

		assertThatThrownBy(() -> userService.createUser(request("an@homestay.local", "GIAM_DOC")))
				.isInstanceOf(InvalidRoleException.class);

		verify(userRepository, never()).save(any());
	}

	@Test
	void taoHopLe_luuMatKhauMaHoa_batDoiMatKhau_vaGuiEmailMatKhauTam() {
		Role receptionist = mock(Role.class);
		when(receptionist.getCode()).thenReturn("RECEPTIONIST");
		when(roleRepository.findByCode("RECEPTIONIST")).thenReturn(Optional.of(receptionist));
		when(passwordEncoder.encode(anyString())).thenReturn("MA_HOA_BCRYPT");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		UserResponse response = userService.createUser(request("  An@Homestay.Local ", "RECEPTIONIST"));

		ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(savedUser.capture());
		assertThat(savedUser.getValue().getPasswordHash()).isEqualTo("MA_HOA_BCRYPT");   // không lưu mật khẩu gốc
		assertThat(savedUser.getValue().isMustChangePassword()).isTrue();
		assertThat(savedUser.getValue().getEmail()).isEqualTo("an@homestay.local");

		ArgumentCaptor<String> temporaryPassword = ArgumentCaptor.forClass(String.class);
		verify(mailService).sendTemporaryPassword(eq(savedUser.getValue()), temporaryPassword.capture());
		assertThat(temporaryPassword.getValue()).hasSize(10).isNotEqualTo("MA_HOA_BCRYPT");
		verify(passwordEncoder).encode(temporaryPassword.getValue());

		assertThat(response.role()).isEqualTo("RECEPTIONIST");
		assertThat(response.mustChangePassword()).isTrue();
	}

	@Test
	void matKhauTam_luonDu10KyTu_coCaChuVaSo() {
		for (int i = 0; i < 50; i++) {
			assertThat(userService.generateTemporaryPassword())
					.hasSize(10)
					.matches(".*[A-Za-z].*")
					.matches(".*\\d.*");
		}
	}

	@Test
	void bieuMau_thieuHoTen_emailSai_soDienThoaiSai_thieuVaiTro_deuBaoLoi() {
		Set<ConstraintViolation<CreateUserRequest>> errors =
				validator.validate(new CreateUserRequest("", "khong-phai-email", "12345", "", true));

		assertThat(errors).hasSize(4);
	}
}