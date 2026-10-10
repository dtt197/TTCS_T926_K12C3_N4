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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "booking_guests", uniqueConstraints = {
        @UniqueConstraint(name = "uk_booking_guests_booking_order", columnNames = {"booking_id", "guest_order"})
})
@Getter
@Setter
@NoArgsConstructor
public class BookingGuest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "guest_order", nullable = false)
    private int guestOrder;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    @Column(name = "identity_number", length = 12)
    private String identityNumber;
}
