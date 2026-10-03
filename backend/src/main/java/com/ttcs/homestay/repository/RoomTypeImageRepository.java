package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.RoomTypeImage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomTypeImageRepository extends JpaRepository<RoomTypeImage, Long> {

    List<RoomTypeImage> findByRoomTypeIdOrderByDisplayOrderAsc(Long roomTypeId);

    long countByRoomTypeId(Long roomTypeId);

    Optional<RoomTypeImage> findFirstByRoomTypeIdOrderByDisplayOrderAsc(Long roomTypeId);
}
