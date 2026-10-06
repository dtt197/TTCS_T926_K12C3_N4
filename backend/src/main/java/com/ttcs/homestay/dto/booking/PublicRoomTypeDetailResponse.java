package com.ttcs.homestay.dto.booking;

import com.ttcs.homestay.dto.amenity.AmenitySummary;
import com.ttcs.homestay.entity.Amenity;
import com.ttcs.homestay.entity.CancellationTier;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.entity.RoomTypeCancellationTier;
import com.ttcs.homestay.entity.RoomTypeImage;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * S2-04: Chi tiết loại phòng công khai cho khách xem trước khi đặt phòng.
 * Bao gồm tên, bộ ảnh, mô tả, tiện nghi, sức chứa, số phòng còn trống, giá,
 * cùng thông tin rõ ràng về:
 * - Giờ nhận phòng (Check-in time)
 * - Giờ trả phòng (Check-out time)
 * - Chính sách trẻ nhỏ (Cho phép hoặc không cho phép mang theo trẻ nhỏ)
 * - Mức phụ thu thêm người khi vượt quá số lượng tiêu chuẩn
 * - Chính sách hủy phòng & các mốc thời gian áp dụng
 * - Mức phí phạt / số tiền hoàn tương ứng theo từng mốc.
 */
public record PublicRoomTypeDetailResponse(
        Long id,
        String code,
        String name,
        int standardCapacity,
        int maxCapacity,
        int numberOfBeds,
        String description,
        Long weekdayPrice,
        Long weekendPrice,
        int availableRooms,
        List<AmenitySummary> amenities,
        List<String> images,
        // S2-04 Policies:
        String checkInTime,
        String checkOutTime,
        boolean allowChildren,
        String childPolicy,
        long extraGuestFee,
        long extraPersonFee,
        String cancellationPolicy,
        List<CancellationTierPolicy> cancellationTiers
) {

    public record CancellationTierPolicy(
            int hoursBeforeCheckIn,
            int refundPercent,
            int feePercent,
            String timeLabel,
            String feeDescription
    ) {}

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public static PublicRoomTypeDetailResponse from(RoomType roomType, int availableRooms) {
        return from(roomType, availableRooms, null);
    }

    public static PublicRoomTypeDetailResponse from(RoomType roomType, int availableRooms, OperatingSettings settings) {
        List<AmenitySummary> activeAmenities = roomType.getAmenities() != null ? roomType.getAmenities().stream()
                .filter(Amenity::isActive)
                .sorted(Comparator.comparing(Amenity::getName))
                .map(AmenitySummary::from)
                .toList() : List.of();

        List<String> images = roomType.getImages() == null
                ? List.of()
                : roomType.getImages().stream()
                        .map(RoomTypeImage::getImageUrl)
                        .filter(imageUrl -> imageUrl != null && !imageUrl.isBlank())
                        .toList();
        if (images.isEmpty() && roomType.getImageUrl() != null && !roomType.getImageUrl().isBlank()) {
            images = List.of(roomType.getImageUrl());
        }

        // 1. Giờ nhận phòng và giờ trả phòng
        LocalTime effectiveCheckIn = roomType.getCheckInTime() != null
                ? roomType.getCheckInTime()
                : (settings != null && settings.getCheckInTime() != null ? settings.getCheckInTime() : LocalTime.of(14, 0));
        LocalTime effectiveCheckOut = roomType.getCheckOutTime() != null
                ? roomType.getCheckOutTime()
                : (settings != null && settings.getCheckOutTime() != null ? settings.getCheckOutTime() : LocalTime.of(12, 0));

        // 2. Chính sách trẻ nhỏ
        boolean allowKids = roomType.getAllowChildren() == null || Boolean.TRUE.equals(roomType.getAllowChildren());
        String effectiveChildPolicy = (roomType.getChildPolicy() != null && !roomType.getChildPolicy().isBlank())
                ? roomType.getChildPolicy().trim()
                : (allowKids
                        ? "Cho phép mang theo trẻ nhỏ. Trẻ dưới 6 tuổi được miễn phí phụ thu khi ngủ chung giường sẵn có với người lớn."
                        : "Không cho phép mang theo trẻ nhỏ (loại phòng này chỉ dành cho người lớn từ 18 tuổi trở lên).");

        // 3. Mức phụ thu thêm người riêng của loại phòng
        long effectiveExtraFee = roomType.getExtraGuestFee() == null ? 0L : roomType.getExtraGuestFee();

        // 4. Chính sách hủy phòng
        String effectiveCancellationPolicy = (roomType.getCancellationPolicy() != null && !roomType.getCancellationPolicy().isBlank())
                ? roomType.getCancellationPolicy().trim()
                : "Chính sách hủy linh hoạt theo mốc thời gian trước khi nhận phòng.";

        // 5. Các mốc hủy phòng và mức phí phạt tương ứng
        List<CancellationTierPolicy> tierPolicies = buildCancellationTiers(roomType, settings);

        return new PublicRoomTypeDetailResponse(
                roomType.getId(),
                roomType.getCode(),
                roomType.getName(),
                roomType.getStandardCapacity(),
                roomType.getMaxCapacity(),
                roomType.getNumberOfBeds(),
                roomType.getDescription(),
                roomType.getWeekdayPrice(),
                roomType.getWeekendPrice(),
                availableRooms,
                activeAmenities,
                images,
                effectiveCheckIn.format(TIME_FORMATTER),
                effectiveCheckOut.format(TIME_FORMATTER),
                allowKids,
                effectiveChildPolicy,
                effectiveExtraFee,
                effectiveExtraFee,
                effectiveCancellationPolicy,
                tierPolicies
        );
    }

    private static List<CancellationTierPolicy> buildCancellationTiers(RoomType roomType, OperatingSettings settings) {
        List<CancellationTierPolicy> result = new ArrayList<>();

        if (roomType.getCancellationTiers() != null && !roomType.getCancellationTiers().isEmpty()) {
            for (RoomTypeCancellationTier tier : roomType.getCancellationTiers()) {
                int hours = tier.getHoursBeforeCheckIn();
                int refund = tier.getRefundPercent();
                int fee = Math.max(0, 100 - refund);
                result.add(new CancellationTierPolicy(hours, refund, fee, formatTimeLabel(hours), formatFeeDescription(refund, fee)));
            }
            return result;
        }

        if (settings != null && settings.getCancellationTiers() != null && !settings.getCancellationTiers().isEmpty()) {
            for (CancellationTier tier : settings.getCancellationTiers()) {
                int hours = tier.getHoursBeforeCheckIn();
                int refund = tier.getRefundPercent();
                int fee = Math.max(0, 100 - refund);
                result.add(new CancellationTierPolicy(hours, refund, fee, formatTimeLabel(hours), formatFeeDescription(refund, fee)));
            }
            return result;
        }

        // Fallback mặc định theo quy chuẩn hệ thống homestay
        result.add(new CancellationTierPolicy(72, 100, 0, formatTimeLabel(72), formatFeeDescription(100, 0)));
        result.add(new CancellationTierPolicy(24, 50, 50, formatTimeLabel(24), formatFeeDescription(50, 50)));
        result.add(new CancellationTierPolicy(0, 0, 100, formatTimeLabel(0), formatFeeDescription(0, 100)));
        return result;
    }

    private static String formatTimeLabel(int hours) {
        if (hours == 0) {
            return "Trong vòng 24 giờ trước giờ nhận phòng hoặc vắng mặt (No-show)";
        }
        return "Trước " + hours + " giờ so với giờ nhận phòng";
    }

    private static String formatFeeDescription(int refundPercent, int feePercent) {
        if (feePercent == 0) {
            return "Miễn phí hủy (hoàn trả 100% tiền cọc)";
        }
        if (feePercent == 100) {
            return "Phí phạt 100% (không hoàn tiền cọc)";
        }
        return "Phí phạt " + feePercent + "% tiền cọc (hoàn lại " + refundPercent + "%)";
    }

}
