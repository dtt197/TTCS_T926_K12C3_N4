package com.ttcs.homestay.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Immutable issued invoice record; only cancellation metadata may change after issuance. */
@Entity
@Table(name = "issued_invoices")
@Getter
@Setter
@NoArgsConstructor
public class IssuedInvoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_issued_invoices_booking"))
    private Booking booking;

    @Column(name = "invoice_year", nullable = false)
    private Short invoiceYear;

    @Column(name = "sequence_number", nullable = false)
    private int sequenceNumber;

    @Column(name = "invoice_number", nullable = false, length = 11)
    private String invoiceNumber;

    @Column(name = "issued_at", nullable = false)
    private OffsetDateTime issuedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issued_by_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_issued_invoices_issued_by_user"))
    private User issuedByUser;

    @Column(name = "issued_by_name", nullable = false, length = 150)
    private String issuedByName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> snapshotJson;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "pdf_bytes", nullable = false, columnDefinition = "bytea")
    private byte[] pdfBytes;

    @Column(name = "pdf_sha256", nullable = false, length = 64)
    private String pdfSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status = Status.ISSUED;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelled_by_user_id",
            foreignKey = @ForeignKey(name = "fk_issued_invoices_cancelled_by_user"))
    private User cancelledByUser;

    @Column(name = "cancellation_reason", length = 500)
    private String cancellationReason;

    public enum Status { ISSUED, CANCELLED }
}
