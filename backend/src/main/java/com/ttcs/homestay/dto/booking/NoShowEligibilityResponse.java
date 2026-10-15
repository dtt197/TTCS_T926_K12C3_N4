package com.ttcs.homestay.dto.booking;

import java.time.OffsetDateTime;

public record NoShowEligibilityResponse(
        Long bookingId,
        String bookingCode,
        boolean eligible,
        String reason,
        OffsetDateTime scheduledCheckIn,
        OffsetDateTime eligibleAfter,
        OffsetDateTime evaluatedAt,
        String status,
        boolean checkedIn
) {}
