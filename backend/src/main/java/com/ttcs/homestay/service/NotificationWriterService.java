package com.ttcs.homestay.service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronization;

import com.ttcs.homestay.entity.MaintenanceRequestEntity;
import com.ttcs.homestay.entity.NotificationEntity;
import com.ttcs.homestay.entity.RoomStatusHistory;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.NotificationRepository;

@Service
public class NotificationWriterService {

    private static final Logger log = LoggerFactory.getLogger(NotificationWriterService.class);

    private static final String ROOM_STATUS_HISTORY = "ROOM_STATUS_HISTORY";
    private static final String ROOM_CLEANED = "ROOM_CLEANED";
    private static final String MAINTENANCE_REQUEST = "MAINTENANCE_REQUEST";
    private static final String MAINTENANCE_REPORTED = "MAINTENANCE_REPORTED";

    private final NotificationRecipientService recipientService;
    private final NotificationRepository notificationRepository;
    private final NotificationEventService notificationEventService;

    public NotificationWriterService(
            NotificationRecipientService recipientService,
            NotificationRepository notificationRepository) {
        this(recipientService, notificationRepository, null);
    }

    @Autowired
    public NotificationWriterService(
            NotificationRecipientService recipientService,
            NotificationRepository notificationRepository,
            NotificationEventService notificationEventService) {
        this.recipientService = recipientService;
        this.notificationRepository = notificationRepository;
        this.notificationEventService = notificationEventService;
    }

    public void writeRoomCleaned(RoomStatusHistory history, User actor) {
        Objects.requireNonNull(history, "Room status history is required");
        Long sourceId = requireSavedId(history.getId(), "Room status history");
        Objects.requireNonNull(actor, "Actor is required");

        requireWritableTransaction();
        List<User> recipients = uniqueRecipients(recipientService.findRecipients());
        java.util.List<Long> recipientIds = new java.util.ArrayList<>();
        for (User recipient : recipients) {
            NotificationEntity notification = new NotificationEntity();
            notification.setRecipient(recipient);
            notification.setActor(actor);
            notification.setRoom(history.getRoom());
            notification.setSourceType(ROOM_STATUS_HISTORY);
            notification.setSourceId(sourceId);
            notification.setEventType(ROOM_CLEANED);
            notification.setRoomStatusHistory(history);
            notification.setMaintenanceRequest(null);
            notification.setCreatedAt(OffsetDateTime.now());
            notification.setReadAt(null);
            notificationRepository.save(notification);
            recipientIds.add(recipient.getId());
        }
        publishAfterCommit(recipientIds);
    }

    public void writeMaintenanceReported(MaintenanceRequestEntity request) {
        Objects.requireNonNull(request, "Maintenance request is required");
        Long sourceId = requireSavedId(request.getId(), "Maintenance request");
        User actor = Objects.requireNonNull(request.getReportedBy(), "Reporter is required");

        requireWritableTransaction();
        List<User> recipients = uniqueRecipients(recipientService.findRecipients());
        java.util.List<Long> recipientIds = new java.util.ArrayList<>();
        for (User recipient : recipients) {
            NotificationEntity notification = new NotificationEntity();
            notification.setRecipient(recipient);
            notification.setActor(actor);
            notification.setRoom(request.getRoom());
            notification.setSourceType(MAINTENANCE_REQUEST);
            notification.setSourceId(sourceId);
            notification.setEventType(MAINTENANCE_REPORTED);
            notification.setRoomStatusHistory(null);
            notification.setMaintenanceRequest(request);
            notification.setCreatedAt(OffsetDateTime.now());
            notification.setReadAt(null);
            notificationRepository.save(notification);
            recipientIds.add(recipient.getId());
        }
        publishAfterCommit(recipientIds);
    }

    public void writeMaintenanceCleaned(MaintenanceRequestEntity request) {
        Objects.requireNonNull(request, "Maintenance request is required");
        Long sourceId = requireSavedId(request.getId(), "Maintenance request");
        User actor = Objects.requireNonNull(request.getCleanedBy(), "Cleaner is required");
        requireWritableTransaction();
        List<User> recipients = uniqueRecipients(recipientService.findRecipients()).stream()
                .filter(user -> notificationRepository.findByRecipient_IdAndSourceTypeAndSourceIdAndEventType(
                        user.getId(), MAINTENANCE_REQUEST, sourceId, "MAINTENANCE_CLEANED").isEmpty())
                .toList();
        java.util.List<Long> recipientIds = new java.util.ArrayList<>();
        for (User recipient : recipients) {
            NotificationEntity notification = new NotificationEntity();
            notification.setRecipient(recipient);
            notification.setActor(actor);
            notification.setRoom(request.getRoom());
            notification.setSourceType(MAINTENANCE_REQUEST);
            notification.setSourceId(sourceId);
            notification.setEventType("MAINTENANCE_CLEANED");
            notification.setRoomStatusHistory(null);
            notification.setMaintenanceRequest(request);
            notification.setCreatedAt(OffsetDateTime.now());
            notification.setReadAt(null);
            notificationRepository.save(notification);
            recipientIds.add(recipient.getId());
        }
        publishAfterCommit(recipientIds);
    }

    private void publishAfterCommit(List<Long> recipientIds) {
        if (recipientIds.isEmpty() || notificationEventService == null) {
            return;
        }
        List<Long> immutableRecipients = List.copyOf(recipientIds);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    notificationEventService.publish(immutableRecipients);
                } catch (RuntimeException exception) {
                    log.warn("Notification SSE publish failed after transaction commit", exception);
                }
            }
        });
    }

    private List<User> uniqueRecipients(List<User> recipients) {
        Map<Long, User> uniqueById = new LinkedHashMap<>();
        for (User recipient : recipients) {
            Objects.requireNonNull(recipient, "Recipient is required");
            Long recipientId = requireSavedId(recipient.getId(), "Recipient");
            uniqueById.putIfAbsent(recipientId, recipient);
        }
        return List.copyOf(uniqueById.values());
    }

    private Long requireSavedId(Long id, String sourceName) {
        if (id == null) {
            throw new IllegalArgumentException(sourceName + " must already be saved");
        }
        return id;
    }

    private void requireWritableTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw new IllegalStateException("Notification writes require an active writable caller transaction");
        }
    }
}
