package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.settings.CancellationTierRequest;
import com.ttcs.homestay.dto.settings.OperatingSettingsRequest;
import com.ttcs.homestay.dto.settings.OperatingSettingsResponse;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.exception.InvalidSettingsException;
import com.ttcs.homestay.exception.SettingsNotFoundException;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S1-09: tham số vận hành theo PHIÊN BẢN.
 * Mỗi lần lưu tạo phiên bản mới kèm người sửa và thời điểm (AC4);
 * phiên bản mới nhất là phiên bản đang áp dụng,
 * booking sẽ dùng phiên bản có hiệu lực lúc tạo booking.
 *
 * S2-01 Lát 3:
 * cấu hình các ngày được tính là cuối tuần.
 */
@Service
public class OperatingSettingsService {

    public static final int MAX_CANCELLATION_TIERS = 3;

    private final OperatingSettingsRepository settingsRepository;

    public OperatingSettingsService(
            OperatingSettingsRepository settingsRepository) {

        this.settingsRepository = settingsRepository;
    }

    @Transactional(readOnly = true)
    public OperatingSettingsResponse getCurrent() {
        return OperatingSettingsResponse.from(
                settingsRepository
                        .findFirstByOrderByCreatedAtDescIdDesc()
                        .orElseThrow(SettingsNotFoundException::new)
        );
    }

    /** AC4: lịch sử thay đổi, mới nhất trước. */
    @Transactional(readOnly = true)
    public List<OperatingSettingsResponse> getHistory() {
        return settingsRepository
                .findTop20ByOrderByCreatedAtDescIdDesc()
                .stream()
                .map(OperatingSettingsResponse::from)
                .toList();
    }

    /**
     * AC4: tham số có hiệu lực tại một thời điểm,
     * dùng khi tạo booking ở Sprint 2.
     */
    @Transactional(readOnly = true)
    public OperatingSettings findEffectiveAt(
            OffsetDateTime at) {

        return settingsRepository
                .findFirstByCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(at)
                .orElseThrow(SettingsNotFoundException::new);
    }

    /**
     * AC4: không sửa đè,
     * luôn tạo phiên bản mới ghi người sửa và thời điểm sửa.
     */
    @Transactional
    public OperatingSettingsResponse update(
            OperatingSettingsRequest request,
            Long userId,
            String userName) {

        validateTimes(request);

        List<CancellationTierRequest> tiers =
                validateCancellationTiers(
                        request.cancellationTiers()
                );

        List<String> weekendDays =
                validateWeekendDays(
                        request.weekendDays()
                );

        OperatingSettings settings =
                new OperatingSettings();

        settings.setHomestayName(
                request.homestayName().trim()
        );

        settings.setAddress(
                blankToNull(request.address())
        );

        settings.setPhone(
                blankToNull(request.phone())
        );

        settings.setEmail(
                blankToNull(request.email())
        );

        settings.setCheckInTime(
                request.checkInTime()
        );

        settings.setCheckOutTime(
                request.checkOutTime()
        );

        settings.setLateCheckoutFeePerHour(
                request.lateCheckoutFeePerHour()
        );

        settings.setExtraPersonFee(
                request.extraPersonFee()
        );

        settings.setWeekendDays(
                String.join(",", weekendDays)
        );

        settings.setCreatedByUserId(userId);

        settings.setCreatedByName(
                userName == null || userName.isBlank()
                        ? "Không rõ"
                        : userName
        );

        settings.setCreatedAt(
                OffsetDateTime.now()
        );

        tiers.forEach(
                tier ->
                        settings.addCancellationTier(
                                tier.hoursBeforeCheckIn(),
                                tier.refundPercent()
                        )
        );

        return OperatingSettingsResponse.from(
                settingsRepository.save(settings)
        );
    }

    /**
     * S2-01 Lát 3:
     * - phải chọn ít nhất một ngày cuối tuần;
     * - chỉ chấp nhận các giá trị của DayOfWeek;
     * - loại bỏ trùng lặp;
     * - chuẩn hoá thành chữ in hoa.
     */
    static List<String> validateWeekendDays(
            List<String> weekendDays) {

        if (weekendDays == null
                || weekendDays.isEmpty()) {

            throw new InvalidSettingsException(
                    "Phải chọn ít nhất một ngày cuối tuần"
            );
        }

        List<String> normalized =
                weekendDays.stream()
                        .map(value ->
                                value == null
                                        ? ""
                                        : value.trim()
                                                .toUpperCase(
                                                        Locale.ROOT
                                                )
                        )
                        .filter(value ->
                                !value.isBlank()
                        )
                        .distinct()
                        .toList();

        if (normalized.isEmpty()) {
            throw new InvalidSettingsException(
                    "Phải chọn ít nhất một ngày cuối tuần"
            );
        }

        for (String day : normalized) {
            try {
                DayOfWeek.valueOf(day);
            } catch (IllegalArgumentException exception) {
                throw new InvalidSettingsException(
                        "Ngày cuối tuần không hợp lệ: "
                                + day
                );
            }
        }

        return normalized;
    }

    /**
     * AC2, AC3:
     * 1–3 mốc;
     * sắp theo số giờ giảm dần;
     * không trùng số giờ;
     * càng sát giờ nhận phòng thì tỷ lệ hoàn phải càng thấp.
     */
    static List<CancellationTierRequest>
            validateCancellationTiers(
                    List<CancellationTierRequest> tiers) {

        if (tiers == null
                || tiers.isEmpty()
                || tiers.size()
                        > MAX_CANCELLATION_TIERS) {

            throw new InvalidSettingsException(
                    "Chính sách huỷ có từ 1 đến "
                            + MAX_CANCELLATION_TIERS
                            + " mốc"
            );
        }

        List<CancellationTierRequest> sorted =
                tiers.stream()
                        .sorted(
                                Comparator.comparing(
                                        CancellationTierRequest
                                                ::hoursBeforeCheckIn
                                ).reversed()
                        )
                        .toList();

        for (int i = 1;
                i < sorted.size();
                i++) {

            CancellationTierRequest earlier =
                    sorted.get(i - 1);

            CancellationTierRequest later =
                    sorted.get(i);

            if (later.hoursBeforeCheckIn()
                    .equals(
                            earlier.hoursBeforeCheckIn()
                    )) {

                throw new InvalidSettingsException(
                        "Có 2 mốc huỷ cùng "
                                + later.hoursBeforeCheckIn()
                                + " giờ trước nhận phòng "
                                + "(chồng lấn), "
                                + "vui lòng sửa lại"
                );
            }

            if (later.refundPercent()
                    >= earlier.refundPercent()) {

                throw new InvalidSettingsException(
                        "Mốc huỷ trước "
                                + later.hoursBeforeCheckIn()
                                + " giờ phải hoàn ít hơn "
                                + "mốc trước "
                                + earlier.hoursBeforeCheckIn()
                                + " giờ ("
                                + earlier.refundPercent()
                                + "%), vì càng sát ngày "
                                + "nhận phòng thì hoàn cọc "
                                + "càng thấp"
                );
            }
        }

        return sorted;
    }

    /**
     * Giờ trả phòng phải trước giờ nhận phòng
     * để buồng phòng kịp dọn.
     */
    private void validateTimes(
            OperatingSettingsRequest request) {

        if (!request.checkOutTime()
                .isBefore(
                        request.checkInTime()
                )) {

            throw new InvalidSettingsException(
                    "Giờ trả phòng ("
                            + request.checkOutTime()
                            + ") phải trước giờ "
                            + "nhận phòng ("
                            + request.checkInTime()
                            + ") để có thời gian "
                            + "dọn phòng"
            );
        }
    }

    private String blankToNull(
            String value) {

        return value == null
                || value.isBlank()
                        ? null
                        : value.trim();
    }
}