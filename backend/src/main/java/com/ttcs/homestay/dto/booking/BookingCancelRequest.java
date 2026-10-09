package com.ttcs.homestay.dto.booking;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** S3-02 Lát 3: lễ tân huỷ booking, bắt buộc nhập lý do. */
public record BookingCancelRequest(
        @NotBlank(message = "Vui lòng nhập lý do huỷ booking")
        @Size(max = 500, message = "Lý do huỷ tối đa 500 ký tự")
        String reason
) {}