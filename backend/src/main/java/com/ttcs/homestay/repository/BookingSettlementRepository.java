package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.BookingSettlement;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingSettlementRepository extends JpaRepository<BookingSettlement, Long> {
    Optional<BookingSettlement> findByBookingId(Long bookingId);
}
