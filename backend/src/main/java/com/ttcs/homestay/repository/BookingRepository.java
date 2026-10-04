package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.OffsetDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;


public interface BookingRepository extends JpaRepository<Booking, Long> {

        List<Booking> findAllByOrderByCreatedAtDescIdDesc();

        /** S2-07: mã booking đã có chưa (sinh lại nếu trùng). */
        boolean existsByBookingCode(String bookingCode);

        /**
         * S2-07: booking của một loại phòng có ít nhất một đêm trong [checkIn,
         * checkOut).
         * Khoảng ngày nửa mở: booking trả phòng ngày 23 không trùng booking nhận phòng
         * ngày 23.
         */
        @Query("select b from Booking b"
                + " where b.roomType.id = :roomTypeId"
                + " and b.checkInDate < :checkOut and b.checkOutDate > :checkIn"
                + " and b.status in :statuses")
        List<Booking> findOverlapping(
                @Param("roomTypeId") Long roomTypeId,
                @Param("checkIn") LocalDate checkIn,
                @Param("checkOut") LocalDate checkOut,
                @Param("statuses") Collection<BookingStatus> statuses);

        List<Booking> findByStatusAndHoldExpiresAtLessThanEqual(
                BookingStatus status,
                OffsetDateTime now);
                @Query("""
    select b from Booking b
    where (:status is null or b.status = :status)
      and (:checkInFrom is null or b.checkInDate >= :checkInFrom)
      and (:checkInTo is null or b.checkInDate <= :checkInTo)
      and (
          :keyword is null
          or lower(b.guestName) like lower(concat('%', :keyword, '%'))
          or lower(coalesce(b.guestPhone, '')) like lower(concat('%', :keyword, '%'))
      )
    """)
Page<Booking> search(
        @Param("status") BookingStatus status,
        @Param("checkInFrom") LocalDate checkInFrom,
        @Param("checkInTo") LocalDate checkInTo,
        @Param("keyword") String keyword,
        Pageable pageable);
}