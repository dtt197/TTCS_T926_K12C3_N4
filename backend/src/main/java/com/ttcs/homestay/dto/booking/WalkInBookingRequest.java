package com.ttcs.homestay.dto.booking;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.math.BigDecimal;

public record WalkInBookingRequest(
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
        @Pattern(
                regexp = "^(03|05|07|08|09)[0-9]{8}$",
                message = "Số điện thoại phải có đúng 10 chữ số Việt Nam"
        )
        String phone,

        @Email(message = "Email không đúng định dạng")
        @Size(max = 150, message = "Email tối đa 150 ký tự")
        String email,

        @NotNull(message = "Vui lòng nhập số khách")
        @Min(value = 1, message = "Số khách ít nhất là 1")
        Integer guestCount,

        @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
        String note,

        @NotNull(message = "Tiá»n cá»c Ä‘Ã£ thu khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
        @Min(value = 0, message = "Tiá»n cá»c khÃ´ng Ä‘Æ°á»£c Ã¢m")
        BigDecimal depositAmount,

        String depositPaymentMethod,

        @Size(max = 255, message = "MÃ£ giao dá»‹ch tá»‘i Ä‘a 255 kÃ½ tá»±")
        String depositPaymentReference
) {
}
