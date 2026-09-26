package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.RoomStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomStatusHistoryRepository
        extends JpaRepository<RoomStatusHistory, Long> {

    @Query("""
        select history
        from RoomStatusHistory history
        where history.room.id = :roomId
        order by history.changedAt asc, history.id asc
    """)
    List<RoomStatusHistory> findByRoomIdInChronologicalOrder(
            @Param("roomId") Long roomId
    );
}