package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.MaintenanceRequestEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface MaintenanceRequestRepository
        extends JpaRepository<MaintenanceRequestEntity, Long> {

    Page<MaintenanceRequestEntity> findByStatus(
            String status, Pageable pageable);

    Optional<MaintenanceRequestEntity> findByIdempotencyKey(
            UUID idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MaintenanceRequestEntity m where m.id = :id")
    Optional<MaintenanceRequestEntity> findByIdForUpdate(
            @Param("id") Long id);

    // Giữ tương thích với IncidentReportService
    boolean existsByRoom_IdAndStatusNot(
            Long roomId, String status);

    // Kiểm tra yêu cầu bảo trì KHÁC đang mở, loại trừ request hiện tại
    @Query("""
            select case when count(m) > 0 then true else false end
            from MaintenanceRequestEntity m
            where m.room.id = :roomId
              and m.status <> :status
              and m.id <> :excludedId
            """)
    boolean existsOtherByRoomIdAndStatusNot(
            @Param("roomId") Long roomId,
            @Param("status") String status,
            @Param("excludedId") Long excludedId);

    @Query("""
            select distinct m.room.id
            from MaintenanceRequestEntity m
            where m.status = 'CHO_VE_SINH'
            order by m.room.id
            """)
    List<Long> findRoomIdsAwaitingCleaning();
}