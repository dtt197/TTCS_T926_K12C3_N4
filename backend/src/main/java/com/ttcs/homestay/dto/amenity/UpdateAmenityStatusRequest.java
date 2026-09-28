package com.ttcs.homestay.dto.amenity;

import jakarta.validation.constraints.NotNull;

public record UpdateAmenityStatusRequest(
        @NotNull(message = "Vui lòng chọn trạng thái sử dụng")
        Boolean active) {
}