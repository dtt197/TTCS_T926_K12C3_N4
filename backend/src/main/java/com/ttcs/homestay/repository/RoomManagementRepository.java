package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomManagementRepository extends JpaRepository<Room, Long> {

    boolean existsByRoomNumberIgnoreCase(String roomNumber);

    boolean existsByRoomNumberIgnoreCaseAndIdNot(String roomNumber, Long id);

    Optional<Room> findByIdAndActiveTrue(Long id);

    @Query("""
    select r from Room r
    where r.active = true
      and (:roomType is null or r.roomType = :roomType)
      and (:floor is null or r.floor = :floor)
      and (:status is null or r.status = :status)
    order by r.roomNumber asc
    """)
List<Room> searchActiveRooms(
        @Param("roomType") String roomType,
        @Param("floor") Integer floor,
        @Param("status") RoomStatus status);
}
