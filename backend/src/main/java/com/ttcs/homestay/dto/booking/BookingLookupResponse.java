package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * S2-08 Lát 1: chi tiết booking trả cho khách tra cứu.
 * Chỉ gồm các trường AC cho phép; không có số giấy tờ tuỳ thân, ghi chú nội bộ của lễ tân,
 * số điện thoại hay ghi chú của khách.
 */
public record BookingLookupResponse(
        String bookingCode,
        String status,
        String statusLabel,
        String roomTypeName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        long nights,
        long totalAmount,
        long depositAmount) {

    public static BookingLookupResponse from(Booking booking, long depositAmount) {
        return new BookingLookupResponse(
                booking.getBookingCode(),
                booking.getStatus().name(),
                statusLabel(booking.getStatus()),
                booking.getRoomTypeNameSnapshot(),
                booking.getCheckInDate(),
                booking.getCheckOutDate(),
                ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate()),
                booking.getTotalAmount(),
                depositAmount);
    }

    static String statusLabel(BookingStatus status) {
        return switch (status) {
            case CHO_XAC_NHAN -> "Chờ xác nhận";
            case DA_XAC_NHAN -> "Đã xác nhận";
            case DA_HUY -> "Đã huỷ";
            case DA_NHAN_PHONG -> "Đã nhận phòng";
            case DA_TRA_PHONG -> "Đã trả phòng";
            case DA_HET_HAN -> "Đã hết hạn giữ chỗ";
        };
    }
}