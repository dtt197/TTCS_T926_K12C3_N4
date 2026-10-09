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
import org.hibernate.annotations.ColumnDefault;
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

    /** S3-02: phÃ²ng cá»¥ thá»ƒ booking Ä‘ang giá»¯; cÆ¡ sá»Ÿ dá»¯ liá»‡u cháº·n hai booking cÃ¹ng chiáº¿m má»™t phÃ²ng trong má»™t Ä‘Ãªm. */
    @ManyToOne
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(name = "room_confirmed_at")
    private OffsetDateTime roomConfirmedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_confirmed_by_user_id")
    private User roomConfirmedByUser;
    
    /** S3-02 LÃ¡t 3: lÃ½ do, ngÆ°á»i huá»· vÃ  thá»i Ä‘iá»ƒm huá»· booking. */
    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Column(name = "cancelled_by", length = 255)
    private String cancelledBy;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 30)
    @ColumnDefault("'TRUC_TUYEN'")
    private BookingSource source = BookingSource.TRUC_TUYEN;

    /** S2-07: thÃ´ng tin khÃ¡ch tá»± Ä‘áº·t trÃªn trang cÃ´ng khai (trá»‘ng vá»›i booking nhÃ¢n viÃªn táº¡o). */
    @Column(name = "guest_phone", length = 20)
    private String guestPhone;

    @Column(name = "guest_email", length = 150)
    private String guestEmail;

    @Column(name = "guest_count")
    private Integer guestCount;

    @Column(name = "note", length = 500)
    private String note;

    /** S2-07: booking chá» xÃ¡c nháº­n chá»‰ giá»¯ chá»— Ä‘áº¿n thá»i Ä‘iá»ƒm nÃ y (táº¡o + 24 giá»). */
    @Column(name = "hold_expires_at")
    private OffsetDateTime holdExpiresAt;
    
    /** S2-06: sá»‘ ngÆ°á»i vÆ°á»£t sá»©c chá»©a tiÃªu chuáº©n, má»©c phá»¥ thu (VND / ngÆ°á»i / Ä‘Ãªm) Ã¡p dá»¥ng vÃ  tiá»n phá»¥ thu. */
    @Column(name = "extra_guest_count", nullable = false)
    private int extraGuestCount;

    @Column(name = "extra_person_fee_snapshot", nullable = false)
    private long extraPersonFeeSnapshot;

    @Column(name = "surcharge_amount", nullable = false)
    private long surchargeAmount;
    /** S3-05: thông tin hủy booking; trống với booking chưa hủy. */

    @Column(name = "cancel_note", length = 500)
    private String cancelNote;

    @Column(name = "cancel_deposit_amount")
    private Long cancelDepositAmount;

    @Column(name = "cancel_refund_percent")
    private Integer cancelRefundPercent;

    @Column(name = "cancel_refund_amount")
    private Long cancelRefundAmount;
}
