package com.ttcs.homestay.dto.roomtype;

import jakarta.validation.constraints.NotNull;

public record UpdateRoomTypeStatusRequest(
        @NotNull(message = "Vui lòng chọn trạng thái bán")
        Boolean active) {
}