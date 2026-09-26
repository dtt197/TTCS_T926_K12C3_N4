package com.ttcs.homestay.dto.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Quy tắc theo S1-03: mật khẩu mới tối thiểu 8 ký tự, gồm cả chữ và số. */
public record ChangePasswordRequest(
		@NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
		String currentPassword,

		@NotBlank(message = "Vui lòng nhập mật khẩu mới")
		@Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,100}$",
				message = "Mật khẩu mới tối thiểu 8 ký tự, gồm cả chữ và số")
		String newPassword) {
}