package com.ttcs.homestay.dto.security;

import java.time.OffsetDateTime;
import java.util.List;

import com.ttcs.homestay.entity.EndpointDiscrepancyTicket;

public record EndpointDiscrepancyResponse(
        Long id,
        String method,
        String path,
        String approvedAccess,
        List<String> approvedRoles,
        String actualAccess,
        List<String> actualRoles,
        String description,
        Long assigneeId,
        String assigneeName,
        String assigneeRole,
        String createdByName,
        OffsetDateTime createdAt
) {
    public static EndpointDiscrepancyResponse from(EndpointDiscrepancyTicket ticket) {
        return new EndpointDiscrepancyResponse(
                ticket.getId(),
                ticket.getMethod(),
                ticket.getPath(),
                ticket.getApprovedAccess(),
                ticket.getApprovedRoles(),
                ticket.getActualAccess(),
                ticket.getActualRoles(),
                ticket.getDescription(),
                ticket.getAssignee().getId(),
                ticket.getAssignee().getFullName(),
                ticket.getAssignee().getRole().getCode(),
                ticket.getCreatedBy().getFullName(),
                ticket.getCreatedAt()
        );
    }
}
