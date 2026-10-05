package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.booking.BookingLookupResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.exception.BookingLookupNotFoundException;
import com.ttcs.homestay.repository.BookingRepository;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S2-08 Lát 1: khách tra cứu lại booking bằng mã booking và email, không cần tài khoản.
 * Chỉ khi cả hai khớp mới trả chi tiết; sai một trong hai trả cùng một thông báo chung.
 */
@Service
public class BookingLookupService {

    private final BookingRepository bookingRepository;

    public BookingLookupService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public BookingLookupResponse lookup(String bookingCode, String email) {
        String code = bookingCode == null ? "" : bookingCode.trim().toUpperCase(Locale.ROOT);
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);

        return bookingRepository.findByBookingCode(code)
                .filter(booking -> emailMatches(booking, normalizedEmail))
                .map(booking -> BookingLookupResponse.from(booking, recordedDeposit(booking)))
                .orElseThrow(BookingLookupNotFoundException::new);
    }

    private static boolean emailMatches(Booking booking, String normalizedEmail) {
        String bookingEmail = booking.getGuestEmail();
        return bookingEmail != null
                && !normalizedEmail.isEmpty()
                && bookingEmail.trim().toLowerCase(Locale.ROOT).equals(normalizedEmail);
    }

    /** Sprint 3 (lễ tân ghi nhận cọc) sẽ cộng các lần ghi nhận cọc; hiện chưa có nên là 0. */
    private static long recordedDeposit(Booking booking) {
        return 0L;
    }
}