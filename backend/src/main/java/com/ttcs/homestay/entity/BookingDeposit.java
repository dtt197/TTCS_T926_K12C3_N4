package com.ttcs.homestay.entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "booking_deposits")
@Getter
@Setter
@NoArgsConstructor
public class BookingDeposit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "payment_method", nullable = false, length = 20)
    private String paymentMethod;

    @Column(name = "received_date")
    private LocalDate receivedDate;

    @Column(name = "payment_reference", length = 255)
    private String paymentReference;

    /** Lát 2: mã tham chiếu không bắt buộc với CASH (dùng khi cần khác nhau). */
    @Column(name = "reservation_code", length = 255)
    private String reservationCode;

    @Column(name = "created_at", nullable = false)
    private java.time.OffsetDateTime createdAt;

    @Column(name = "created_by", length = 120)
    private String createdBy;

    @PrePersist
    void onCreate() {
        createdAt = java.time.OffsetDateTime.now();
    }
}
