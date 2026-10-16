package com.ttcs.homestay.dto.booking;

public record CustomerNoShowResponse(
        String phone,
        String fullName,
        int noShowCount,
        boolean hasWarning,
        String warningMessage
) {}
