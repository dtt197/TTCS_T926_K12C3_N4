package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
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

    @Query("select b from Booking b join fetch b.room r"
            + " where b.status = :status and b.roomConfirmedAt is not null and r.status = :roomStatus"
            + " order by b.checkInDate asc, b.bookingCode asc")
    List<Booking> findCheckInOptions(
            @Param("status") BookingStatus status,
            @Param("roomStatus") RoomStatus roomStatus);

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

    /** S3-02: booking đang giữ một trong các phòng và có ít nhất một đêm trong [checkIn, checkOut). */
    @Query("select b from Booking b"
            + " where b.room in :rooms"
            + " and b.checkInDate < :checkOut and b.checkOutDate > :checkIn"
            + " and b.status in :statuses")
    List<Booking> findOverlappingOnRooms(
            @Param("rooms") Collection<Room> rooms,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut,
            @Param("statuses") Collection<BookingStatus> statuses);

    /** Booking đang chiếm phòng cụ thể trong ít nhất một đêm của khoảng nửa mở. */
    @Query("select b from Booking b"
            + " where b.room.id = :roomId and b.id <> :excludeBookingId"
            + " and b.checkInDate < :checkOut and b.checkOutDate > :checkIn"
            + " and b.status in :statuses order by b.bookingCode asc")
    List<Booking> findRoomConflicts(
            @Param("roomId") Long roomId,
            @Param("excludeBookingId") Long excludeBookingId,
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

    @EntityGraph(attributePaths = {"roomConfirmedByUser", "room", "roomType"})
    @Override
    Page<Booking> findAll(org.springframework.data.jpa.domain.Specification<Booking> spec, Pageable pageable);

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
