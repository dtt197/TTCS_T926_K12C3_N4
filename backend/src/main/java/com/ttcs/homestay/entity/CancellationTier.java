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

/** S1-09 AC2: một mốc chính sách huỷ, huỷ trước ít nhất N giờ thì hoàn X% tiền cọc. */
@Entity
@Table(name = "cancellation_policy_tiers")
@Getter
@Setter
@NoArgsConstructor
public class CancellationTier {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settings_id", nullable = false)
    private OperatingSettings settings;

    @Column(name = "hours_before_check_in", nullable = false)
    private int hoursBeforeCheckIn;

    @Column(name = "refund_percent", nullable = false)
    private int refundPercent;
}