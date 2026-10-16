package com.ttcs.homestay.entity;

import java.time.OffsetDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "security_endpoint_discrepancies")
public class EndpointDiscrepancyTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "endpoint_method", nullable = false, length = 10)
    private String method;

    @Column(name = "endpoint_path", nullable = false, length = 512)
    private String path;

    @Column(name = "approved_access", nullable = false, length = 20)
    private String approvedAccess;

    @Column(name = "approved_roles", nullable = false, length = 100)
    private String approvedRoles;

    @Column(name = "actual_access", nullable = false, length = 20)
    private String actualAccess;

    @Column(name = "actual_roles", nullable = false, length = 100)
    private String actualRoles;

    @Column(nullable = false, length = 2000)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignee_id", nullable = false)
    private User assignee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_id", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected EndpointDiscrepancyTicket() {
    }

    public EndpointDiscrepancyTicket(
            String method,
            String path,
            String approvedAccess,
            List<String> approvedRoles,
            String actualAccess,
            List<String> actualRoles,
            String description,
            User assignee,
            User createdBy
    ) {
        this.method = method;
        this.path = path;
        this.approvedAccess = approvedAccess;
        this.approvedRoles = String.join(",", approvedRoles);
        this.actualAccess = actualAccess;
        this.actualRoles = String.join(",", actualRoles);
        this.description = description;
        this.assignee = assignee;
        this.createdBy = createdBy;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getApprovedAccess() {
        return approvedAccess;
    }

    public List<String> getApprovedRoles() {
        return splitRoles(approvedRoles);
    }

    public String getActualAccess() {
        return actualAccess;
    }

    public List<String> getActualRoles() {
        return splitRoles(actualRoles);
    }

    public String getDescription() {
        return description;
    }

    public User getAssignee() {
        return assignee;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    private static List<String> splitRoles(String roles) {
        return roles.isBlank() ? List.of() : List.of(roles.split(","));
    }
}
