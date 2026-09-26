package com.ttcs.homestay.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Sửa tài khoản: không có email (email là tên đăng nhập, không cho đổi). */
public record UpdateUserRequest(
		@NotBlank(message = "Vui lòng nhập họ tên")
		@Size(max = 150, message = "Họ tên tối đa 150 ký tự")
		String fullName,

		@Pattern(regexp = "^$|^0[0-9]{9}$", message = "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0")
		String phone,

		@NotBlank(message = "Vui lòng chọn vai trò")
		String role) {
}