package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomManagementRepository extends JpaRepository<Room, Long> {

    boolean existsByRoomNumberIgnoreCase(String roomNumber);

    boolean existsByRoomNumberIgnoreCaseAndIdNot(String roomNumber, Long id);

    @Query("""
    select r from Room r
    where (:roomType is null or r.roomType = :roomType)
      and (:floor is null or r.floor = :floor)
      and (:status is null or r.status = :status)
      and (:active is null or r.active = :active)
    order by r.roomNumber asc
    """)
List<Room> searchRooms(
        @Param("roomType") String roomType,
        @Param("floor") Integer floor,
        @Param("status") RoomStatus status,
        @Param("active") Boolean active);
}
