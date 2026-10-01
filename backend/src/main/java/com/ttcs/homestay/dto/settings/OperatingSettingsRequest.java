package com.ttcs.homestay.dto.settings;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalTime;
import java.util.List;

/**
 * S1-09: thông tin homestay, giờ nhận/trả phòng, phụ thu và chính sách huỷ.
 * S2-01 Lát 3: cấu hình các ngày được tính là cuối tuần.
 */
public record OperatingSettingsRequest(

        @NotBlank(message = "Vui lòng nhập tên homestay")
        @Size(max = 150, message = "Tên homestay tối đa 150 ký tự")
        String homestayName,

        @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
        String address,

        @Pattern(
                regexp = "^$|^0[0-9]{9}$",
                message = "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0"
        )
        String phone,

        @Email(message = "Email không đúng định dạng")
        @Size(max = 255, message = "Email tối đa 255 ký tự")
        String email,

        @NotNull(message = "Vui lòng nhập giờ nhận phòng")
        LocalTime checkInTime,

        @NotNull(message = "Vui lòng nhập giờ trả phòng")
        LocalTime checkOutTime,

        @NotNull(message = "Vui lòng nhập phụ thu trả muộn theo giờ")
        @Min(value = 0, message = "Phụ thu trả muộn không được âm")
        @Max(value = 100_000_000, message = "Phụ thu trả muộn quá lớn")
        Long lateCheckoutFeePerHour,

        @NotNull(message = "Vui lòng nhập phụ thu thêm người")
        @Min(value = 0, message = "Phụ thu thêm người không được âm")
        @Max(value = 100_000_000, message = "Phụ thu thêm người quá lớn")
        Long extraPersonFee,

        @NotNull(message = "Vui lòng khai báo chính sách huỷ")
        @Size(min = 1, max = 3, message = "Chính sách huỷ có từ 1 đến 3 mốc")
        List<@Valid CancellationTierRequest> cancellationTiers,

        /**
         * S2-01 Lát 3:
         * Ví dụ ["FRIDAY", "SATURDAY"].
         */
        @NotNull(message = "Vui lòng chọn ngày cuối tuần")
        @NotEmpty(message = "Phải chọn ít nhất một ngày cuối tuần")
        List<String> weekendDays

) {
}