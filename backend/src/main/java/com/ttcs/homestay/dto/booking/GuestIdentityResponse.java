package com.ttcs.homestay.dto.booking;

/** Số giấy tờ đầy đủ của một khách; mỗi lần trả về đều đã được ghi nhật ký. */
public record GuestIdentityResponse(Long guestId, String fullName, String identityNumber) {
}
