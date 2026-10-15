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
        String note,

        /* S4-02 Lát 4: để trống thì dùng giá mặc định; khác giá mặc định thì bắt buộc có lý do. */
        @Min(value = 0, message = "Đơn giá không được âm")
        @Max(value = 100_000_000, message = "Đơn giá tối đa 100.000.000 đ")
        Long unitPrice,

        @Size(max = 500, message = "Lý do sửa giá tối đa 500 ký tự")
        String priceReason
) {

    /** Giữ constructor của lát 1 (luôn dùng giá mặc định) cho code và test hiện có. */
    public BookingSurchargeRequest(SurchargeType type, Integer quantity, Integer nights, String note) {
        this(type, quantity, nights, note, null, null);
    }
}