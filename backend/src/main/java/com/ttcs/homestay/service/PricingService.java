package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.pricing.NightlyPrice;
import com.ttcs.homestay.dto.pricing.PriceQuoteResponse;
import com.ttcs.homestay.dto.pricing.PriceType;
import com.ttcs.homestay.entity.PriceOverride;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.InvalidPriceQuoteException;
import com.ttcs.homestay.exception.RoomTypeNotFoundException;
import com.ttcs.homestay.repository.PriceOverrideRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S2-02 Lát 2: tính giá từng đêm. Ưu tiên: giá đè > giá cuối tuần > giá ngày thường.
 * Nhận phòng ngày A, trả phòng ngày B gồm các đêm A .. B-1 (đêm của ngày trả phòng không tính).
 */
@Service
public class PricingService {

    /** Giống giới hạn tra phòng trống của S2-05. */
    public static final int MAX_QUOTE_NIGHTS = 30;

    private final RoomTypeRepository roomTypeRepository;
    private final PriceOverrideRepository priceOverrideRepository;
    private final OperatingSettingsService operatingSettingsService;

    public PricingService(
            RoomTypeRepository roomTypeRepository,
            PriceOverrideRepository priceOverrideRepository,
            OperatingSettingsService operatingSettingsService) {
        this.roomTypeRepository = roomTypeRepository;
        this.priceOverrideRepository = priceOverrideRepository;
        this.operatingSettingsService = operatingSettingsService;
    }

    /** API xem giá: dùng ngày cuối tuần trong tham số đang áp dụng. */
    @Transactional(readOnly = true)
    public PriceQuoteResponse quote(Long roomTypeId, LocalDate checkIn, LocalDate checkOut) {
        if (!checkOut.isAfter(checkIn)) {
            throw new InvalidPriceQuoteException("Ngày trả phòng phải sau ngày nhận phòng ít nhất một đêm");
        }
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > MAX_QUOTE_NIGHTS) {
            throw new InvalidPriceQuoteException("Chỉ tính giá tối đa " + MAX_QUOTE_NIGHTS + " đêm một lần");
        }
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(RoomTypeNotFoundException::new);
        Set<DayOfWeek> weekendDays = parseWeekendDays(
                operatingSettingsService.findEffectiveAt(OffsetDateTime.now()).getWeekendDays());

        List<NightlyPrice> nightlyPrices = priceNights(roomType, checkIn, checkOut, weekendDays);
        return new PriceQuoteResponse(
                roomType.getId(),
                roomType.getName(),
                checkIn,
                checkOut,
                nightlyPrices.size(),
                nightlyPrices,
                total(nightlyPrices));
    }

    /** Giá từng đêm từ checkIn đến trước checkOut. Booking và API xem giá cùng dùng hàm này. */
    @Transactional(readOnly = true)
    public List<NightlyPrice> priceNights(
            RoomType roomType, LocalDate checkIn, LocalDate checkOut, Set<DayOfWeek> weekendDays) {
        LocalDate lastNight = checkOut.minusDays(1);
        List<PriceOverride> overrides =
                priceOverrideRepository.findOverlapping(roomType.getId(), checkIn, lastNight);

        List<NightlyPrice> nightlyPrices = new ArrayList<>();
        for (LocalDate night = checkIn; night.isBefore(checkOut); night = night.plusDays(1)) {
            nightlyPrices.add(priceForNight(roomType, night, weekendDays, overrides));
        }
        return nightlyPrices;
    }

    /** Giá một đêm. overrides xếp đợt cập nhật gần nhất trước, như findOverlapping trả về. */
    private NightlyPrice priceForNight(
            RoomType roomType, LocalDate night, Set<DayOfWeek> weekendDays, List<PriceOverride> overrides) {
        for (PriceOverride override : overrides) {
            if (!night.isBefore(override.getStartDate()) && !night.isAfter(override.getEndDate())) {
                return new NightlyPrice(night, PriceType.OVERRIDE, override.getName(), override.getPricePerNight());
            }
        }
        if (weekendDays.contains(night.getDayOfWeek())) {
            return new NightlyPrice(night, PriceType.WEEKEND, "Cuối tuần",
                    requirePrice(roomType.getWeekendPrice(), "Loại phòng chưa có giá cuối tuần"));
        }
        return new NightlyPrice(night, PriceType.WEEKDAY, "Ngày thường",
                requirePrice(roomType.getWeekdayPrice(), "Loại phòng chưa có giá ngày thường"));
    }

    public static long total(List<NightlyPrice> nightlyPrices) {
        long total = 0;
        for (NightlyPrice nightlyPrice : nightlyPrices) {
            total = Math.addExact(total, nightlyPrice.price());
        }
        return total;
    }

    /** Chuỗi trong tham số hệ thống, ví dụ "FRIDAY,SATURDAY", thành tập ngày trong tuần. */
    public static Set<DayOfWeek> parseWeekendDays(String weekendDays) {
        return Arrays.stream(weekendDays.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> DayOfWeek.valueOf(value.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toSet());
    }

    private static long requirePrice(Long price, String message) {
        if (price == null || price <= 0) {
            throw new InvalidPriceQuoteException(message);
        }
        return price;
    }
}