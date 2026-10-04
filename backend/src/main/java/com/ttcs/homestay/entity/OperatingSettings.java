package com.ttcs.homestay.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * S1-09: một PHIÊN BẢN tham số vận hành. Không sửa đè: mỗi lần lưu tạo phiên bản mới (AC4),
 * phiên bản mới nhất là phiên bản đang áp dụng.
 */
@Entity
@Table(name = "operating_settings")
@Getter
@Setter
@NoArgsConstructor
public class OperatingSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "homestay_name", nullable = false, length = 150)
    private String homestayName;

    @Column(length = 255)
    private String address;

    @Column(length = 20)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(name = "check_in_time", nullable = false)
    private LocalTime checkInTime;

    @Column(name = "check_out_time", nullable = false)
    private LocalTime checkOutTime;

    @Column(name = "late_checkout_fee_per_hour", nullable = false)
    private long lateCheckoutFeePerHour;

    @Column(name = "extra_person_fee", nullable = false)
    private long extraPersonFee;

        /**
     * S2-01 Lát 3:
     * Các ngày được tính là cuối tuần, lưu dạng FRIDAY,SATURDAY.
     */
    @Column(name = "weekend_days", nullable = false, length = 100)
    private String weekendDays = "FRIDAY,SATURDAY";

    /** AC4: người sửa. Lưu kèm tên để lịch sử vẫn đọc được nếu tài khoản bị đổi tên. */
    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_by_name", nullable = false, length = 150)
    private String createdByName;

    /** AC4: thời điểm sửa, cũng là thời điểm phiên bản bắt đầu có hiệu lực. */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** AC2, AC3: tối đa 3 mốc huỷ, sắp theo số giờ giảm dần. */
    @OneToMany(mappedBy = "settings", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.EAGER)
    @OrderBy("hoursBeforeCheckIn DESC")
    private List<CancellationTier> cancellationTiers = new ArrayList<>();

    public void addCancellationTier(int hoursBeforeCheckIn, int refundPercent) {
        CancellationTier tier = new CancellationTier();
        tier.setSettings(this);
        tier.setHoursBeforeCheckIn(hoursBeforeCheckIn);
        tier.setRefundPercent(refundPercent);
        cancellationTiers.add(tier);
    }
}