package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.CancelReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CancelBookingRequest(
        @NotNull(message = "Vui lòng chọn lý do huỷ") CancelReason reason,
        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note
) {
}