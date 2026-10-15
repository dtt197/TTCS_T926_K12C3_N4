package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.SurchargeType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** S4-02 Lát 1: lễ tân chỉ gửi loại, số lượng, số đêm, ghi chú. Đơn giá do hệ thống tự lấy. */
public record BookingSurchargeRequest(

        @NotNull(message = "Vui lòng chọn loại phụ thu")
        SurchargeType type,

        @NotNull(message = "Vui lòng nhập số lượng")
        @Min(value = 1, message = "Số lượng phải từ 1 trở lên")
        @Max(value = 10, message = "Số lượng tối đa là 10")
        Integer quantity,

        @NotNull(message = "Vui lòng nhập số đêm áp dụng")
        @Min(value = 1, message = "Số đêm áp dụng phải từ 1 trở lên")
        Integer nights,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note
) {
}