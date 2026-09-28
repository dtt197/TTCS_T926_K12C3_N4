package com.ttcs.homestay.dto.audit;

import java.util.List;

public record AuditLogPage(
        List<AuditLogEntry> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {
}