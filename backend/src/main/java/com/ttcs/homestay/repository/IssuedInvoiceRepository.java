package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.IssuedInvoice;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;

public interface IssuedInvoiceRepository extends JpaRepository<IssuedInvoice, Long> {
    @EntityGraph(attributePaths = {"cancelledByUser"})
    Optional<IssuedInvoice> findByBookingId(Long bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invoice from IssuedInvoice invoice where invoice.booking.id = :bookingId")
    Optional<IssuedInvoice> findByBookingIdForUpdate(@Param("bookingId") Long bookingId);

    Optional<IssuedInvoice> findByInvoiceNumber(String invoiceNumber);
}
