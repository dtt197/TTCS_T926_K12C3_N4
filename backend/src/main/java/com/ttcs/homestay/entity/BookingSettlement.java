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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "booking_settlements")
@Getter
@Setter
@NoArgsConstructor
public class BookingSettlement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "room_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal roomTotal;

    @Column(name = "deposit_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal depositTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "settlement_type", nullable = false, length = 10)
    private SettlementType settlementType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private SettlementPaymentMethod paymentMethod;

    @Column(name = "payment_reference", length = 255)
    private String paymentReference;

    @Column(name = "transaction_status", nullable = false, length = 20)
    private String transactionStatus;

    @Column(name = "acted_by", nullable = false, length = 120)
    private String actedBy;

    @Column(name = "settled_at", nullable = false)
    private OffsetDateTime settledAt;

    @Column(name = "checked_out_at", nullable = false)
    private OffsetDateTime checkedOutAt;

    public enum SettlementType { COLLECT, ZERO, REFUND }
    public enum SettlementPaymentMethod { CASH, BANK_TRANSFER }
}
