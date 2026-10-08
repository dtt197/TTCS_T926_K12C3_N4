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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<Booking> findByStatusAndHoldExpiresAtLessThanEqual(
            BookingStatus status,
            OffsetDateTime now);

    /** S3-09: tìm danh sách booking nhận phòng trong ngày theo danh sách trạng thái. */
    @Query("select b from Booking b where b.checkInDate = :checkInDate and b.status in :statuses")
    List<Booking> findByCheckInDateAndStatusIn(
            @Param("checkInDate") LocalDate checkInDate,
            @Param("statuses") Collection<BookingStatus> statuses);

    /** S3-08: tìm danh sách booking còn hiệu lực trong khoảng ngày quét. */
    @Query("select b from Booking b where b.checkInDate < :endDate and b.checkOutDate > :startDate and b.status in :statuses")
    List<Booking> findActiveBookingsInDateRange(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("statuses") Collection<BookingStatus> statuses);

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
