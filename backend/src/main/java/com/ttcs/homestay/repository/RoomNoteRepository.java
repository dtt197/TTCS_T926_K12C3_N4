package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.RoomNote;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomNoteRepository extends JpaRepository<RoomNote, Long> {

    Optional<RoomNote> findByRoomId(Long roomId);
}
