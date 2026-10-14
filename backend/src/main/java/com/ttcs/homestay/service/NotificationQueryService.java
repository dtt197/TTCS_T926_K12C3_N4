package com.ttcs.homestay.service;

import java.time.OffsetDateTime;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttcs.homestay.dto.notification.NotificationPage;
import com.ttcs.homestay.dto.notification.NotificationResponse;
import com.ttcs.homestay.dto.notification.NotificationUnreadCount;
import com.ttcs.homestay.entity.NotificationEntity;
import com.ttcs.homestay.exception.NotificationNotFoundException;
import com.ttcs.homestay.repository.NotificationRepository;

@Service
public class NotificationQueryService {

    private static final Set<String> ALLOWED_ROLES = Set.of("OWNER", "RECEPTIONIST");

    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;

    public NotificationQueryService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional(readOnly = true)
    public NotificationPage findInbox(Jwt jwt, int requestedPage, int requestedSize) {
        Long recipientId = authenticatedRecipientId(jwt);
        int page = Math.max(0, requestedPage);
        int size = requestedSize <= 0
                ? DEFAULT_PAGE_SIZE
                : Math.min(requestedSize, MAX_PAGE_SIZE);
        Page<NotificationEntity> result = notificationRepository.findByRecipient_Id(
                recipientId,
                PageRequest.of(page, size, Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.desc("id"))));
        return new NotificationPage(
                result.getContent().stream().map(NotificationResponse::from).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCount unreadCount(Jwt jwt) {
        return new NotificationUnreadCount(
                notificationRepository.countByRecipient_IdAndReadAtIsNull(authenticatedRecipientId(jwt)));
    }

    @Transactional
    public void markRead(Jwt jwt, Long notificationId) {
        Long recipientId = authenticatedRecipientId(jwt);
        NotificationEntity notification = notificationRepository
                .findByIdAndRecipient_Id(notificationId, recipientId)
                .orElseThrow(NotificationNotFoundException::new);
        if (notification.getReadAt() == null) {
            notification.setReadAt(OffsetDateTime.now());
        }
    }

    private Long authenticatedRecipientId(Jwt jwt) {
        if (jwt == null || !ALLOWED_ROLES.contains(jwt.getClaimAsString("role"))) {
            throw new AccessDeniedException("Only active owners and receptionists may access notifications");
        }
        try {
            return Long.valueOf(jwt.getSubject());
        } catch (RuntimeException exception) {
            throw new AccessDeniedException("Invalid authenticated user", exception);
        }
    }
}
