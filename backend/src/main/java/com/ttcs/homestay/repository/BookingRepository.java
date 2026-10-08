package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

    List<Booking> findAllByOrderByCreatedAtDescIdDesc();

    /** S2-07: mã booking đã có chưa (sinh lại nếu trùng). */
    boolean existsByBookingCode(String bookingCode);
    
    /** S2-08: tìm booking theo mã để khách tra cứu. */
    Optional<Booking> findByBookingCode(String bookingCode);

    /**
     * S2-07: booking của một loại phòng có ít nhất một đêm trong [checkIn, checkOut).
     * Khoảng ngày nửa mở: booking trả phòng ngày 23 không trùng booking nhận phòng ngày 23.
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

    /**
     * S3-08: Tìm tất cả booking có trạng thái hợp lệ trong khoảng quét ngày [scanStart, scanEnd].
     */
    @Query("select b from Booking b left join fetch b.roomType"
            + " where b.status in :statuses"
            + " and b.checkInDate <= :scanEnd and b.checkOutDate > :scanStart")
    List<Booking> findActiveBookingsInDateRange(
            @Param("scanStart") LocalDate scanStart,
            @Param("scanEnd") LocalDate scanEnd,
            @Param("statuses") Collection<BookingStatus> statuses);

    List<Booking> findByStatusAndHoldExpiresAtLessThanEqual(
            BookingStatus status,
            OffsetDateTime now);

    default Page<Booking> search(
            BookingStatus status,
            LocalDate checkInFrom,
            LocalDate checkInTo,
            String keyword,
            Pageable pageable) {
        return findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (checkInFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("checkInDate"), checkInFrom));
            }
            if (checkInTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("checkInDate"), checkInTo));
            }
            if (keyword != null && !keyword.isBlank()) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                Predicate namePredicate = cb.like(cb.lower(root.get("guestName")), pattern);
                Predicate phonePredicate = cb.like(cb.lower(cb.coalesce(root.get("guestPhone"), "")), pattern);
                predicates.add(cb.or(namePredicate, phonePredicate));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pageable);
    }
}