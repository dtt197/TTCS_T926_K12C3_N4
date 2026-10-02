package com.ttcs.homestay.dto.booking;

 feature/S2-10/booking-pagination
import com.ttcs.homestay.entity.Booking; // Nhớ import entity Booking nếu chưa có

import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.service.BookingHoldPolicy; // Import policy kiểm tra quá hạn
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record BookingResponse(
    Long id,
    String bookingCode,
    String guestName,
    Long roomTypeId,
    String roomTypeNameSnapshot,
    LocalDate checkInDate,
    LocalDate checkOutDate,
    Long weekdayPriceSnapshot,
    Long weekendPriceSnapshot,
    String weekendDaysSnapshot,
    Long totalAmount,
    BookingStatus status,
    OffsetDateTime createdAt,
    boolean holdExpired // Thêm trường kiểm tra quá hạn
) {
  feature/S2-10/booking-pagination
    // Thêm phương thức from này vào để ánh xạ từ Entity sang DTO
    public static BookingResponse from(Booking booking) {

    // Phương thức ánh xạ từ Entity sang DTO kèm logic tính holdExpired
    public static BookingResponse from(Booking booking) {
        boolean expired = BookingHoldPolicy.isExpired(
            booking.getStatus(), 
            booking.getCreatedAt() != null ? booking.getCreatedAt().toInstant() : null, 
            java.time.Instant.now()
        );

        return new BookingResponse(
            booking.getId(),
            booking.getBookingCode(),
            booking.getGuestName(),
            booking.getRoomType() != null ? booking.getRoomType().getId() : null,
            booking.getRoomTypeNameSnapshot(),
            booking.getCheckInDate(),
            booking.getCheckOutDate(),
            booking.getWeekdayPriceSnapshot(),
            booking.getWeekendPriceSnapshot(),
            booking.getWeekendDaysSnapshot(),
            booking.getTotalAmount(),
            booking.getStatus(),
feature/S2-10/booking-pagination
            booking.getCreatedAt()

            booking.getCreatedAt(),
            expired

        );
    }
}