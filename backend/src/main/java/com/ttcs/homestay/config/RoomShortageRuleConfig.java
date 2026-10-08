package com.ttcs.homestay.config;

import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.RoomStatus;
import java.util.Collections;
import java.util.Set;

/**
 * Task #s3-08: Toàn bộ quy tắc nghiệp vụ cảnh báo thiếu phòng được đặt tại MỘT chỗ để dễ cấu hình và thay đổi:
 * 1. Booking còn hiệu lực: CHO_XAC_NHAN, DA_XAC_NHAN, DA_NHAN_PHONG.
 *    Loại ra: DA_HUY, DA_TRA_PHONG, DA_HET_HAN.
 * 2. Số phòng khả dụng: tổng số phòng thực có - (bảo trì + đang khoá + ngừng bán).
 *    (Chỉ tính phòng active = true và status != BAO_TRI tại thời điểm tải màn hình).
 * 3. Khoảng quét: từ hôm nay đến hôm nay + 30 ngày.
 * 4. Booking nhiều đêm tính từng đêm lưu trú [checkInDate, checkOutDate).
 */
public final class RoomShortageRuleConfig {

    private RoomShortageRuleConfig() {
        // Utility / Configuration class
    }

    /** Số ngày quét tính từ ngày hiện tại (hôm nay + 30 ngày). */
    public static final int SCAN_DAYS_AHEAD = 30;

    /**
     * Các trạng thái booking được coi là CÒN HIỆU LỰC (đã xác nhận, chờ thanh toán/giữ chỗ tạm, đang ở).
     */
    public static final Set<BookingStatus> VALID_BOOKING_STATUSES = Collections.unmodifiableSet(Set.of(
            BookingStatus.CHO_XAC_NHAN,
            BookingStatus.DA_XAC_NHAN,
            BookingStatus.DA_NHAN_PHONG
    ));

    /**
     * Các trạng thái booking bị LOẠI RA (đã huỷ, đã trả phòng, đã hết hạn / no-show).
     */
    public static final Set<BookingStatus> EXCLUDED_BOOKING_STATUSES = Collections.unmodifiableSet(Set.of(
            BookingStatus.DA_HUY,
            BookingStatus.DA_TRA_PHONG,
            BookingStatus.DA_HET_HAN
    ));

    /**
     * Trạng thái phòng không khả dụng (đang bảo trì).
     */
    public static final Set<RoomStatus> UNAVAILABLE_ROOM_STATUSES = Collections.unmodifiableSet(Set.of(
            RoomStatus.BAO_TRI
    ));

    /**
     * Có loại trừ booking CHO_XAC_NHAN đã quá hạn giữ chỗ 24h (holdExpiresAt <= now) hay không.
     */
    public static final boolean EXCLUDE_EXPIRED_HOLD = true;
}

