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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * S2-04: Mốc chính sách hủy phòng tùy chỉnh dành riêng cho từng loại phòng.
 */
@Entity
@Table(name = "room_type_cancellation_tiers")
@Getter
@Setter
@NoArgsConstructor
public class RoomTypeCancellationTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @Column(name = "hours_before_check_in", nullable = false)
    private int hoursBeforeCheckIn;

    @Column(name = "refund_percent", nullable = false)
    private int refundPercent;

    public RoomTypeCancellationTier(RoomType roomType, int hoursBeforeCheckIn, int refundPercent) {
        this.roomType = roomType;
        this.hoursBeforeCheckIn = hoursBeforeCheckIn;
        this.refundPercent = refundPercent;
    }
}
