package com.ttcs.homestay.repository;

import java.util.Optional;

import com.ttcs.homestay.entity.NotificationEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {

    @EntityGraph(attributePaths = {"room", "actor"})
    Page<NotificationEntity> findByRecipient_Id(Long recipientId, Pageable pageable);

    long countByRecipient_IdAndReadAtIsNull(Long recipientId);

    Optional<NotificationEntity> findByIdAndRecipient_Id(Long id, Long recipientId);

    Optional<NotificationEntity> findByRecipient_IdAndSourceTypeAndSourceIdAndEventType(
            Long recipientId, String sourceType, Long sourceId, String eventType);
}
