package com.ttcs.homestay.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "maintenance_requests")
@Getter
@Setter
@NoArgsConstructor
public class MaintenanceRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(nullable = false, length = 20)
    private String severity;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, length = 30)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_by", nullable = false)
    private User reportedBy;

    @Column(name = "reported_at", nullable = false)
    private OffsetDateTime reportedAt;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_by")
    private User acceptedBy;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "vendor_name", length = 200)
    private String vendorName;

    @Column(name = "vendor_phone", length = 30)
    private String vendorPhone;

    @Column(name = "vendor_notes", length = 2000)
    private String vendorNotes;

    @Column(name = "processing_started_at")
    private OffsetDateTime processingStartedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repair_completed_by")
    private User repairCompletedBy;
    @Column(name = "repair_completed_at")
    private OffsetDateTime repairCompletedAt;
    @Column(name = "repair_notes", length = 2000)
    private String repairNotes;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by")
    private User verifiedBy;
    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;
    @Column(name = "verification_notes", length = 2000)
    private String verificationNotes;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cleaned_by")
    private User cleanedBy;
    @Column(name = "cleaned_at")
    private OffsetDateTime cleanedAt;
    @Column(name = "cleaning_notes", length = 2000)
    private String cleaningNotes;
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
