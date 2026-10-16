package com.ttcs.homestay.dto.booking;

import java.time.OffsetDateTime;

public record NoShowUndoEligibilityResponse(
        Long bookingId,
        String bookingCode,
        boolean eligible,
        String reason,
        OffsetDateTime markedAt,
        OffsetDateTime expiresAt,
        long remainingSeconds,
        Long receptionistId,
        String receptionistName
) {}
