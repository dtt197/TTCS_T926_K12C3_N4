package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.BookingDepositAdjustment;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BookingDepositAdjustmentRepository extends JpaRepository<BookingDepositAdjustment, Long> {

    List<BookingDepositAdjustment> findAllByBookingIdOrderByCreatedAtAscIdAsc(Long bookingId);

    @org.springframework.data.jpa.repository.Query(
            "select coalesce(sum(case when a.adjustmentType = com.ttcs.homestay.entity.DepositAdjustmentType.TANG "
                    + "then a.amount else -a.amount end), 0) "
                    + "from BookingDepositAdjustment a where a.booking.id = :bookingId")
    BigDecimal sumSignedAmountsByBookingId(@org.springframework.data.repository.query.Param("bookingId") Long bookingId);
}
