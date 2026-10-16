package com.ttcs.homestay.dto.booking;

import java.time.OffsetDateTime;

public record AppointmentActionLogResponse(
        Long id,
        Long appointmentId,
        Long receptionistId,
        String receptionistName,
        String actionType,
        String previousStatus,
        OffsetDateTime createdAt
) {}
