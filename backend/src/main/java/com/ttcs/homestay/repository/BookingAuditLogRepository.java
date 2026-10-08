package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.BookingAuditLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingAuditLogRepository extends JpaRepository<BookingAuditLog, Long> {

    @Query("""
        SELECT log FROM BookingAuditLog log
        WHERE log.booking.id = :bookingId
        ORDER BY log.createdAt DESC, log.id DESC
    """)
    List<BookingAuditLog> findByBookingIdOrderByCreatedAtDescIdDesc(@Param("bookingId") Long bookingId);

    @Query("""
        SELECT COUNT(log) FROM BookingAuditLog log
        WHERE log.booking.id = :bookingId
    """)
    long countByBookingId(@Param("bookingId") Long bookingId);
}
