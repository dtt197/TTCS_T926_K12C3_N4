package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.BookingSurcharge;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingSurchargeRepository extends JpaRepository<BookingSurcharge, Long> {

    List<BookingSurcharge> findByBookingIdOrderByCreatedAtAscIdAsc(Long bookingId);
}