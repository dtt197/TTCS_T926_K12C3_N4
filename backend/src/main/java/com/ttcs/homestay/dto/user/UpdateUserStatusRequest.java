package com.ttcs.homestay.dto.user;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
		@NotNull(message = "Vui lòng chọn trạng thái hoạt động")
		Boolean active) {
}