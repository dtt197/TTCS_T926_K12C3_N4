package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.BookingRoomChangeHistory;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRoomChangeHistoryRepository extends JpaRepository<BookingRoomChangeHistory, Long> {
    @EntityGraph(attributePaths = {"booking", "oldRoom", "newRoom", "actor"})
    List<BookingRoomChangeHistory> findAllByBookingIdOrderByChangedAtDescIdDesc(Long bookingId);
}
