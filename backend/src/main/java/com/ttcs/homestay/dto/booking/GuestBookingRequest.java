package com.ttcs.homestay.dto.booking;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * S2-07 Lát 1: thông tin khách điền trên trang đặt phòng công khai.
 * Định dạng số điện thoại Việt Nam và email kiểm tra ở Lát 2.
 */
public record GuestBookingRequest(
        @NotNull(message = "Vui lòng chọn loại phòng")
        Long roomTypeId,

        @NotNull(message = "Vui lòng chọn ngày nhận phòng")
        LocalDate checkInDate,

        @NotNull(message = "Vui lòng chọn ngày trả phòng")
        LocalDate checkOutDate,

        @NotBlank(message = "Vui lòng nhập họ tên")
        @Size(max = 120, message = "Họ tên tối đa 120 ký tự")
        String guestName,

        @NotBlank(message = "Vui lòng nhập số điện thoại")
        @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
        String phone,

        @NotBlank(message = "Vui lòng nhập email")
        @Size(max = 150, message = "Email tối đa 150 ký tự")
        String email,

        @NotNull(message = "Vui lòng nhập số khách")
        @Min(value = 1, message = "Số khách ít nhất là 1")
        Integer guestCount,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note) {
}