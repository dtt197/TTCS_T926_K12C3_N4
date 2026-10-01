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
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id")
    private RoomType roomType;

    @Column(name = "room_type_name_snapshot", nullable = false, length = 100)
    private String roomTypeNameSnapshot;

    @Column(name = "check_in_date", nullable = false)
    private LocalDate checkInDate;

    @Column(name = "check_out_date", nullable = false)
    private LocalDate checkOutDate;

    @Column(name = "weekday_price_snapshot", nullable = false)
    private long weekdayPriceSnapshot;

    @Column(name = "weekend_price_snapshot", nullable = false)
    private long weekendPriceSnapshot;

    @Column(name = "weekend_days_snapshot", nullable = false, length = 100)
    private String weekendDaysSnapshot;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "booking_code", nullable = false, unique = true, length = 40)
private String bookingCode;

@Column(name = "guest_name", nullable = false, length = 120)
private String guestName;

@Enumerated(EnumType.STRING)
@Column(name = "status", nullable = false, length = 30)
private BookingStatus status;
}