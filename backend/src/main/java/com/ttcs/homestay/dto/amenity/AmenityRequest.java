package com.ttcs.homestay.dto.amenity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** S1-08 AC1: tiện nghi gồm mã, tên và biểu tượng. */
public record AmenityRequest(
        @NotBlank(message = "Vui lòng nhập mã tiện nghi")
        @Pattern(regexp = "^[A-Za-z0-9_]{2,50}$",
                message = "Mã tiện nghi dài 2–50 ký tự, chỉ gồm chữ không dấu, số và dấu gạch dưới")
        String code,

        @NotBlank(message = "Vui lòng nhập tên tiện nghi")
        @Size(max = 100, message = "Tên tiện nghi tối đa 100 ký tự")
        String name,

        @NotBlank(message = "Vui lòng chọn biểu tượng")
        @Size(max = 20, message = "Biểu tượng tối đa 20 ký tự")
        String icon) {
}