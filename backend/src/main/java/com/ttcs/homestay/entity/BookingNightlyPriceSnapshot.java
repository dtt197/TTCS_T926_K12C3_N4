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
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "booking_nightly_price_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class BookingNightlyPriceSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "night_date", nullable = false)
    private LocalDate nightDate;

    @Column(name = "price_type", nullable = false, length = 20)
    private String priceType;

    @Column(name = "price_label", nullable = false, length = 100)
    private String priceLabel;

    @Column(name = "room_price", nullable = false)
    private long roomPrice;

    @Column(name = "surcharge_amount", nullable = false)
    private long surchargeAmount;
}
