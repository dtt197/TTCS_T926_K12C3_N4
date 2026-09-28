package com.ttcs.homestay.dto.audit;

import java.time.OffsetDateTime;

public record AuditLogEntry(
        long id,
        OffsetDateTime occurredAt,
        String actorEmail,
        String accountEmail,
        String action,
        String result,
        String ipAddress) {
}