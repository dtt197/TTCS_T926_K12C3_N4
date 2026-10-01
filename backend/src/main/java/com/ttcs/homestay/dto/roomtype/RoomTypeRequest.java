package com.ttcs.homestay.dto.roomtype;

import jakarta.validation.constraints.Min;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** S1-06 AC1: loại phòng gồm mã, tên, sức chứa tiêu chuẩn, sức chứa tối đa, số giường và mô tả. */
public record RoomTypeRequest(
        @NotBlank(message = "Vui lòng nhập mã loại phòng")
        @Pattern(regexp = "^[A-Za-z0-9_]{2,50}$",
                message = "Mã loại phòng dài 2–50 ký tự, chỉ gồm chữ không dấu, số và dấu gạch dưới")
        String code,

        @NotBlank(message = "Vui lòng nhập tên loại phòng")
        @Size(max = 80, message = "Tên loại phòng tối đa 80 ký tự")
        String name,

        @NotNull(message = "Vui lòng nhập sức chứa tiêu chuẩn")
        @Min(value = 1, message = "Sức chứa tiêu chuẩn tối thiểu là 1 người")
        Integer standardCapacity,

        @NotNull(message = "Vui lòng nhập sức chứa tối đa")
        @Min(value = 1, message = "Sức chứa tối đa tối thiểu là 1 người")
        Integer maxCapacity,

        @NotNull(message = "Vui lòng nhập số giường")
        @Min(value = 1, message = "Số giường tối thiểu là 1")
        Integer numberOfBeds,

        @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
        String description,

        Boolean active,

        /** S1-08: danh sách tiện nghi được tick; null nghĩa là giữ nguyên tiện nghi đang gắn. */
        List<Long> amenityIds) {
}