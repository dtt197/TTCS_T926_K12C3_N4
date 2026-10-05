package com.ttcs.homestay.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "room_types")
@Getter
@Setter
@NoArgsConstructor
public class RoomType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "standard_capacity", nullable = false)
    private Integer standardCapacity;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity;

    @Column(name = "number_of_beds", nullable = false)
    private Integer numberOfBeds;

    @Column(name = "description", length = 500)
private String description;

@Column(name = "weekday_price")
private Long weekdayPrice;

@Column(name = "weekend_price")
private Long weekendPrice;

    @Column(name = "status")
    private Boolean status = true; // true: Đang bán, false: Ngừng bán

    /**
     * S2-04: Các chính sách áp dụng cho loại phòng (nếu null sẽ thừa hưởng từ tham số vận hành chung).
     */
    @Column(name = "check_in_time")
    private java.time.LocalTime checkInTime;

    @Column(name = "check_out_time")
    private java.time.LocalTime checkOutTime;

    @Column(name = "allow_children")
    private Boolean allowChildren = true;

    @Column(name = "child_policy", length = 500)
    private String childPolicy;

    @Column(name = "extra_person_fee")
    private Long extraPersonFee;

    public long effectiveExtraPersonFee(Long fallbackFee) {
        if (extraPersonFee != null) {
            return extraPersonFee;
        }
        if (fallbackFee == null) {
            throw new IllegalStateException("Operating settings are required when the room type has no extra person fee");
        }
        return fallbackFee;
    }

    @Column(name = "cancellation_policy", length = 500)
    private String cancellationPolicy;

    @OneToMany(mappedBy = "roomType", cascade = CascadeType.ALL, orphanRemoval = true, fetch = jakarta.persistence.FetchType.EAGER)
    @OrderBy("hoursBeforeCheckIn DESC")
    private List<RoomTypeCancellationTier> cancellationTiers = new ArrayList<>();

    public void addCancellationTier(int hoursBeforeCheckIn, int refundPercent) {
        RoomTypeCancellationTier tier = new RoomTypeCancellationTier(this, hoursBeforeCheckIn, refundPercent);
        this.cancellationTiers.add(tier);
    }

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "image_alt", length = 200)
    private String imageAlt;

    /** S1-08 AC2: một loại phòng gắn được nhiều tiện nghi (bảng nối room_type_amenities). */
    @ManyToMany
    @JoinTable(
            name = "room_type_amenities",
            joinColumns = @JoinColumn(name = "room_type_id"),
            inverseJoinColumns = @JoinColumn(name = "amenity_id"))
    private Set<Amenity> amenities = new HashSet<>();

    /** S2-09: danh sách ảnh của loại phòng theo thứ tự hiển thị */
    @OneToMany(mappedBy = "roomType", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<RoomTypeImage> images = new ArrayList<>();
}