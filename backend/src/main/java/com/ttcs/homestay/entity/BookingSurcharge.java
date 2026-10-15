package com.ttcs.homestay.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/**
 * S4-02 Lát 1: một dòng phụ thu của booking.
 * Đơn giá được chụp lại lúc thêm, đổi tham số sau này không ảnh hưởng dòng cũ.
 */
@Entity
@Table(name = "booking_surcharges")
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class BookingSurcharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, updatable = false)
    private Booking booking;

    @Enumerated(EnumType.STRING)
    @Column(name = "surcharge_type", nullable = false, length = 20, updatable = false)
    private SurchargeType surchargeType;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, updatable = false)
    private long unitPrice;

    @Column(name = "nights", nullable = false, updatable = false)
    private int nights;

    @Column(name = "amount", nullable = false, updatable = false)
    private long amount;

    @Column(name = "note", length = 500, updatable = false)
    private String note;

    @Column(name = "created_by", nullable = false, length = 120, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}