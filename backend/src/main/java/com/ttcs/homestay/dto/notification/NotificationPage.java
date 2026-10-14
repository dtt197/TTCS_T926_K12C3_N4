package com.ttcs.homestay.dto.notification;

import java.util.List;

public record NotificationPage(
        List<NotificationResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}
