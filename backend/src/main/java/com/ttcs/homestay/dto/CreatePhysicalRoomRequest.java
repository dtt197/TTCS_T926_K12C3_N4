package com.ttcs.homestay.dto;

import com.ttcs.homestay.entity.RoomStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePhysicalRoomRequest(
        @NotBlank(message = "Số phòng không được để trống")
        @Size(max = 20, message = "Số phòng tối đa 20 ký tự")
        String roomNumber,

        @NotNull(message = "Tầng không được để trống")
        @Min(value = 0, message = "Tầng phải lớn hơn hoặc bằng 0")
        Integer floor,

        @NotBlank(message = "Loại phòng không được để trống")
        @Size(max = 80, message = "Loại phòng tối đa 80 ký tự")
        String roomType,

        RoomStatus status
) {
}
