package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.BookingDeposit;
import com.ttcs.homestay.entity.Booking;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingDepositRepository extends JpaRepository<BookingDeposit, Long> {

    /** Lát 1: mỗi booking chỉ lưu được tối đa 1 khoản cọc ban đầu. */
    Optional<BookingDeposit> findByBooking(Booking booking);

    boolean existsByBooking(Booking booking);
}
