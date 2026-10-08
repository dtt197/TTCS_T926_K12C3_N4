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

@Entity
@Table(name = "booking_audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class BookingAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "booking_code", length = 40)
    private String bookingCode;

    @Column(name = "old_check_in_date", nullable = false)
    private LocalDate oldCheckInDate;

    @Column(name = "new_check_in_date", nullable = false)
    private LocalDate newCheckInDate;

    @Column(name = "old_check_out_date", nullable = false)
    private LocalDate oldCheckOutDate;

    @Column(name = "new_check_out_date", nullable = false)
    private LocalDate newCheckOutDate;

    @Column(name = "old_room_type_id")
    private Long oldRoomTypeId;

    @Column(name = "old_room_type_name", nullable = false, length = 100)
    private String oldRoomTypeName;

    @Column(name = "new_room_type_id")
    private Long newRoomTypeId;

    @Column(name = "new_room_type_name", nullable = false, length = 100)
    private String newRoomTypeName;

    @Column(name = "old_total_amount", nullable = false)
    private long oldTotalAmount;

    @Column(name = "new_total_amount", nullable = false)
    private long newTotalAmount;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_name", length = 150)
    private String actorName;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
