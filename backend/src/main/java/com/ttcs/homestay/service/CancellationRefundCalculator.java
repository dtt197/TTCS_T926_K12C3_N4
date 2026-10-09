package com.ttcs.homestay.service;

import com.ttcs.homestay.entity.CancellationTier;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/** S3-05: tính tỷ lệ và số tiền hoàn cọc từ các mốc huỷ và tiền cọc đã ghi nhận. */
public final class CancellationRefundCalculator {

    public static final ZoneId HOMESTAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private CancellationRefundCalculator() {
    }

    /** appliedTierHours là số giờ của mốc đang áp dụng, null nếu không thoả mốc nào. */
    public record Result(Integer appliedTierHours, int refundPercent, long refundAmount) {
    }

    /** Số giờ trọn vẹn còn lại đến giờ nhận phòng; đã quá giờ nhận phòng thì trả -1. */
    public static long hoursUntilCheckIn(
            OffsetDateTime cancelAt, LocalDate checkInDate, LocalTime checkInTime) {
        Duration remaining = Duration.between(
                cancelAt.toInstant(),
                checkInDate.atTime(checkInTime).atZone(HOMESTAY_ZONE).toInstant());
        return remaining.isNegative() ? -1 : remaining.toHours();
    }

    /** Lấy mốc có số giờ lớn nhất mà thời điểm huỷ còn thoả; không thoả mốc nào thì hoàn 0%. */
    public static Result calculate(
            List<CancellationTier> tiers, long hoursBeforeCheckIn, long depositAmount) {
        CancellationTier applied = tiers.stream()
                .sorted(Comparator.comparingInt(CancellationTier::getHoursBeforeCheckIn).reversed())
                .filter(tier -> hoursBeforeCheckIn >= tier.getHoursBeforeCheckIn())
                .findFirst()
                .orElse(null);
        int percent = applied == null ? 0 : applied.getRefundPercent();
        Integer hours = applied == null ? null : applied.getHoursBeforeCheckIn();
        long deposit = Math.max(depositAmount, 0);
        return new Result(hours, percent, deposit * percent / 100);
    }
}