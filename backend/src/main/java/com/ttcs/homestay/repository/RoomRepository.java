package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.Room;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface RoomRepository extends JpaRepository<Room, Long> {

    List<Room> findAllByActiveTrueOrderByRoomNumberAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select room from Room room where room.id = :id")
    Optional<Room> findByIdForUpdate(Long id);
        /** S2-07: các phòng đang hoạt động của một loại phòng (phòng gắn loại phòng theo tên). */
    List<Room> findByRoomTypeIgnoreCaseAndActiveTrue(String roomType);

    /** S1-06: đếm số phòng đang gắn với một loại phòng (so theo tên, không phân biệt hoa thường). */
    long countByRoomTypeIgnoreCase(String roomType);

    /** S1-06: đổi tên loại phòng thì đổi luôn tên loại phòng lưu trong các phòng để không mất liên kết. */
    @Modifying
    @Query("update Room room set room.roomType = :newName where lower(room.roomType) = lower(:oldName)")
    int renameRoomType(@Param("oldName") String oldName, @Param("newName") String newName);
}