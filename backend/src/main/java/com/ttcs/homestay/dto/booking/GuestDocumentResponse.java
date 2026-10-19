package com.ttcs.homestay.dto.booking;

/**
 * Bảo vệ dữ liệu cá nhân: khách lưu trú kèm số giấy tờ đã che, chỉ để lộ 4 ký tự cuối.
 * hasIdentityNumber cho biết có số giấy tờ để bấm "Xem đầy đủ" hay không.
 */
public record GuestDocumentResponse(
        Long guestId,
        String fullName,
        boolean primary,
        String maskedIdentityNumber,
        boolean hasIdentityNumber) {
}
