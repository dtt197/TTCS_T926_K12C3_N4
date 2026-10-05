package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.booking.BookingLookupResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.exception.BookingLookupLimitException;
import com.ttcs.homestay.exception.BookingLookupNotFoundException;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S2-08 Lát 1: khách tra cứu lại booking bằng mã booking và email, không cần tài khoản.
 * Chỉ khi cả hai khớp mới trả chi tiết; sai một trong hai trả cùng một thông báo chung.
 * S2-08 Lát 2: IP đã tra cứu sai 10 lần trong 15 phút thì bị từ chối mọi lượt tra cứu.
 * S2-08 Lát 3: kèm giờ nhận/trả phòng theo tham số vận hành có hiệu lực lúc tạo booking (S1-09 AC4).
 */
@Service
public class BookingLookupService {

    /** Giờ mặc định theo S1-09 khi chưa có tham số vận hành. */
    static final LocalTime DEFAULT_CHECK_IN_TIME = LocalTime.of(14, 0);
    static final LocalTime DEFAULT_CHECK_OUT_TIME = LocalTime.of(12, 0);

    private final BookingRepository bookingRepository;
    private final BookingLookupRateLimiter rateLimiter;
    private final OperatingSettingsRepository settingsRepository;

    @Autowired
    public BookingLookupService(
            BookingRepository bookingRepository,
            BookingLookupRateLimiter rateLimiter,
            OperatingSettingsRepository settingsRepository) {
        this.bookingRepository = bookingRepository;
        this.rateLimiter = rateLimiter;
        this.settingsRepository = settingsRepository;
    }

    public BookingLookupService(BookingRepository bookingRepository, BookingLookupRateLimiter rateLimiter) {
        this(bookingRepository, rateLimiter, null);
    }

    public BookingLookupService(BookingRepository bookingRepository) {
        this(bookingRepository, new BookingLookupRateLimiter());
    }

    @Transactional(readOnly = true)
    public BookingLookupResponse lookup(String bookingCode, String email) {
        return lookup(bookingCode, email, null);
    }

    @Transactional(readOnly = true)
    public BookingLookupResponse lookup(String bookingCode, String email, String ipAddress) {
        rateLimiter.blockedUntil(ipAddress).ifPresent(until -> {
            throw new BookingLookupLimitException(until);
        });

        String code = bookingCode == null ? "" : bookingCode.trim().toUpperCase(Locale.ROOT);
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);

        Optional<Booking> match = bookingRepository.findByBookingCode(code)
                .filter(booking -> emailMatches(booking, normalizedEmail));
        if (match.isEmpty()) {
            rateLimiter.recordFailure(ipAddress);
            throw new BookingLookupNotFoundException();
        }

        Booking booking = match.get();
        Optional<OperatingSettings> settings = settingsFor(booking);
        return BookingLookupResponse.from(
                booking,
                recordedDeposit(booking),
                settings.map(OperatingSettings::getCheckInTime).orElse(DEFAULT_CHECK_IN_TIME),
                settings.map(OperatingSettings::getCheckOutTime).orElse(DEFAULT_CHECK_OUT_TIME));
    }

    /** Tham số có hiệu lực lúc tạo booking; booking cũ hơn mọi phiên bản thì dùng phiên bản hiện tại. */
    private Optional<OperatingSettings> settingsFor(Booking booking) {
        if (settingsRepository == null) {
            return Optional.empty();
        }
        OffsetDateTime createdAt = booking.getCreatedAt() == null ? OffsetDateTime.now() : booking.getCreatedAt();
        return settingsRepository.findFirstByCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(createdAt)
                .or(settingsRepository::findFirstByOrderByCreatedAtDescIdDesc);
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