package com.ttcs.homestay.dto.maintenance;

import com.ttcs.homestay.entity.MaintenanceRequestEntity;
import java.time.OffsetDateTime;

public record MaintenanceResponse(
        Long id, Long roomId, String roomNumber, String severity, String description,
        String status, Long reportedById, String reportedByName, OffsetDateTime reportedAt,
        Long acceptedById, String acceptedByName, OffsetDateTime acceptedAt,
        String vendorName, String vendorPhone, String vendorNotes, OffsetDateTime processingStartedAt,
        Long repairCompletedById, String repairCompletedByName, OffsetDateTime repairCompletedAt, String repairNotes,
        Long verifiedById, String verifiedByName, OffsetDateTime verifiedAt, String verificationNotes,
        Long cleanedById, String cleanedByName, OffsetDateTime cleanedAt, String cleaningNotes, OffsetDateTime completedAt) {
    public static MaintenanceResponse from(MaintenanceRequestEntity m) {
        return new MaintenanceResponse(m.getId(), m.getRoom().getId(), m.getRoom().getRoomNumber(),
                m.getSeverity(), m.getDescription(), m.getStatus(), m.getReportedBy().getId(),
                m.getReportedBy().getFullName(), m.getReportedAt(),
                m.getAcceptedBy() == null ? null : m.getAcceptedBy().getId(),
                m.getAcceptedBy() == null ? null : m.getAcceptedBy().getFullName(), m.getAcceptedAt(),
                m.getVendorName(), m.getVendorPhone(), m.getVendorNotes(), m.getProcessingStartedAt(),
                m.getRepairCompletedBy() == null ? null : m.getRepairCompletedBy().getId(),
                m.getRepairCompletedBy() == null ? null : m.getRepairCompletedBy().getFullName(),
                m.getRepairCompletedAt(), m.getRepairNotes(),
                m.getVerifiedBy() == null ? null : m.getVerifiedBy().getId(),
                m.getVerifiedBy() == null ? null : m.getVerifiedBy().getFullName(),
                m.getVerifiedAt(), m.getVerificationNotes(),
                m.getCleanedBy() == null ? null : m.getCleanedBy().getId(),
                m.getCleanedBy() == null ? null : m.getCleanedBy().getFullName(),
                m.getCleanedAt(), m.getCleaningNotes(), m.getCompletedAt());
    }
}
