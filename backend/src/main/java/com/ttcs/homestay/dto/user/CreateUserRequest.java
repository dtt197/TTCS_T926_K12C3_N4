package com.ttcs.homestay.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
		@NotBlank(message = "Vui lòng nhập họ tên")
		@Size(max = 150, message = "Họ tên tối đa 150 ký tự")
		String fullName,

		@NotBlank(message = "Vui lòng nhập email")
		@Email(message = "Email không đúng định dạng")
		@Size(max = 255, message = "Email tối đa 255 ký tự")
		String email,

		@Pattern(regexp = "^$|^0[0-9]{9}$", message = "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0")
		String phone,

		@NotBlank(message = "Vui lòng chọn vai trò")
		String role,

		Boolean active) {
}