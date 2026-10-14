package com.ttcs.homestay.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "maintenance_request_history")
@Getter
@Setter
public class MaintenanceRequestHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "maintenance_request_id", nullable = false, updatable = false)
    private Long maintenanceRequestId;
    @Column(nullable = false, length = 40, updatable = false)
    private String action;
    @Column(name = "previous_status", nullable = false, length = 30, updatable = false)
    private String previousStatus;
    @Column(name = "new_status", nullable = false, length = 30, updatable = false)
    private String newStatus;
    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private Long actorUserId;
    @Column(name = "actor_name", nullable = false, length = 150, updatable = false)
    private String actorName;
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;
    @Column(name = "vendor_name", length = 200, updatable = false)
    private String vendorName;
    @Column(name = "vendor_phone", length = 30, updatable = false)
    private String vendorPhone;
    @Column(length = 2000, updatable = false)
    private String notes;
}
