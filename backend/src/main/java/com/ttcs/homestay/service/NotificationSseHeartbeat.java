package com.ttcs.homestay.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationSseHeartbeat {

    private final NotificationEventService notificationEventService;

    public NotificationSseHeartbeat(NotificationEventService notificationEventService) {
        this.notificationEventService = notificationEventService;
    }

    @Scheduled(fixedDelay = 20_000)
    public void sendHeartbeats() {
        notificationEventService.heartbeat();
    }
}
