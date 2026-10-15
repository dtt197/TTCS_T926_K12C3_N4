package com.ttcs.homestay.dto.notification;

import java.time.OffsetDateTime;

import com.ttcs.homestay.entity.NotificationEntity;

public record NotificationResponse(
        Long id,
        String eventType,
        Long roomId,
        String roomNumber,
        Long actorUserId,
        String actorName,
        String sourceType,
        Long sourceId,
        OffsetDateTime createdAt,
        OffsetDateTime readAt) {

    public static NotificationResponse from(NotificationEntity notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getEventType(),
                notification.getRoom().getId(),
                notification.getRoom().getRoomNumber(),
                notification.getActor().getId(),
                notification.getActor().getFullName(),
                notification.getSourceType(),
                notification.getSourceId(),
                notification.getCreatedAt(),
                notification.getReadAt());
    }
}
