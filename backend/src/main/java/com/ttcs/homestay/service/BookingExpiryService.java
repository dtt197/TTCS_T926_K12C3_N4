package com.ttcs.homestay.service;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.repository.BookingRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingExpiryService {

    private final BookingRepository bookingRepository;

    public BookingExpiryService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
    }

    /**
     * S2-07 Lát 4:
     * Booking chờ xác nhận quá 24 giờ sẽ chuyển sang ĐÃ HẾT HẠN.
     *
     * Chạy mỗi phút để không phải chờ người dùng truy cập trang.
     */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireBookings() {

        OffsetDateTime now = OffsetDateTime.now();

        List<Booking> expiredBookings =
                bookingRepository.findByStatusAndHoldExpiresAtLessThanEqual(
                        BookingStatus.CHO_XAC_NHAN,
                        now);

        for (Booking booking : expiredBookings) {
            booking.setStatus(BookingStatus.DA_HET_HAN);
        }

        if (!expiredBookings.isEmpty()) {
            bookingRepository.saveAll(expiredBookings);
        }
    }
}