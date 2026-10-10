package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.BookingGuest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingGuestRepository extends JpaRepository<BookingGuest, Long> {

    List<BookingGuest> findAllByBookingIdOrderByGuestOrderAsc(Long bookingId);
}
