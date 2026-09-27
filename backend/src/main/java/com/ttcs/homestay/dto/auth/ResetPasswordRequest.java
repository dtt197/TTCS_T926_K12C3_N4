package com.ttcs.homestay.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Giữ đúng quy tắc mật khẩu như ChangePasswordRequest (S1-03). */
public record ResetPasswordRequest(
		@NotBlank(message = "Thiếu mã đặt lại mật khẩu")
		String token,

		@NotBlank(message = "Vui lòng nhập mật khẩu mới")
		@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,100}$",
				message = "Mật khẩu mới tối thiểu 8 ký tự, gồm cả chữ và số")
		String newPassword) {
}
