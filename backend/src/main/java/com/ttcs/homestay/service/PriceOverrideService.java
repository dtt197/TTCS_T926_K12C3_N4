package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.pricing.PreviewNight;
import com.ttcs.homestay.dto.pricing.PriceOverridePreviewRequest;
import com.ttcs.homestay.dto.pricing.PriceOverridePreviewResponse;
import com.ttcs.homestay.dto.pricing.PriceOverrideRequest;
import com.ttcs.homestay.dto.pricing.PriceOverrideResponse;
import com.ttcs.homestay.entity.PriceOverride;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.InvalidPriceOverrideException;
import com.ttcs.homestay.exception.PriceOverrideConflictException;
import com.ttcs.homestay.exception.PriceOverrideNotFoundException;
import com.ttcs.homestay.repository.PriceOverrideRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S2-02 Lát 1: thêm, xem, sửa, xoá đợt giá đè theo mùa hoặc ngày lễ.
 * Lát 3 (AC3): hai đợt của cùng loại phòng không được trùng đêm nào.
 * Lát 4 (AC4): xem trước giá từng đêm trước khi lưu.
 */
@Service
public class PriceOverrideService {

    /** Một đợt giá đè tối đa 366 đêm (một năm). */
    public static final long MAX_NIGHTS = 366;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PriceOverrideRepository priceOverrideRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final PricingService pricingService;

    public PriceOverrideService(PriceOverrideRepository priceOverrideRepository,
            RoomTypeRepository roomTypeRepository,
            PricingService pricingService) {
        this.priceOverrideRepository = priceOverrideRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.pricingService = pricingService;
    }

    @Transactional(readOnly = true)
    public List<PriceOverrideResponse> listPriceOverrides() {
        return priceOverrideRepository.findAllByOrderByStartDateAscIdAsc().stream()
                .map(PriceOverrideResponse::from)
                .toList();
    }

    @Transactional
    public PriceOverrideResponse createPriceOverride(PriceOverrideRequest request, String createdByName) {
        validateDates(request.startDate(), request.endDate());
        RoomType roomType = findRoomType(request.roomTypeId());
        checkNoOverlap(request, null);
        PriceOverride priceOverride = new PriceOverride();
        apply(priceOverride, request, roomType);
        OffsetDateTime now = OffsetDateTime.now();
        priceOverride.setCreatedByName(createdByName == null || createdByName.isBlank() ? "Không rõ" : createdByName);
        priceOverride.setCreatedAt(now);
        priceOverride.setUpdatedAt(now);
        return PriceOverrideResponse.from(priceOverrideRepository.save(priceOverride));
    }

    @Transactional
    public PriceOverrideResponse updatePriceOverride(Long id, PriceOverrideRequest request) {
        PriceOverride priceOverride = findOrThrow(id);
        validateDates(request.startDate(), request.endDate());
        RoomType roomType = findRoomType(request.roomTypeId());
        // Kiểm tra trùng trước khi sửa đợt, để câu truy vấn không đọc phải dữ liệu đang sửa dở.
        checkNoOverlap(request, id);
        apply(priceOverride, request, roomType);
        priceOverride.setUpdatedAt(OffsetDateTime.now());
        return PriceOverrideResponse.from(priceOverride);
    }

    @Transactional
    public void deletePriceOverride(Long id) {
        priceOverrideRepository.delete(findOrThrow(id));
    }

    /**
     * Lát 4: bảng giá từng đêm trong khoảng ngày của đợt (gồm cả hai đầu), không lưu gì.
     * Mỗi đêm có giá hiện tại (không tính đợt đang sửa) và giá sau khi lưu; kèm cảnh báo nếu trùng đợt khác.
     */
    @Transactional(readOnly = true)
    public PriceOverridePreviewResponse preview(PriceOverridePreviewRequest request) {
        validateDates(request.startDate(), request.endDate());
        RoomType roomType = findRoomType(request.roomTypeId());
        Long newPrice = request.pricePerNight();

        List<PreviewNight> nights = pricingService
                .currentPrices(roomType, request.startDate(), request.endDate(), request.excludeId())
                .stream()
                .map(night -> new PreviewNight(night.date(), night.priceType(), night.label(), night.price(), newPrice))
                .toList();

        boolean allPriced = nights.stream().allMatch(night -> night.currentPrice() != null);
        Long currentTotal = allPriced ? nights.stream().mapToLong(night -> night.currentPrice()).sum() : null;
        Long newTotal = newPrice == null ? null : newPrice * nights.size();
        String conflict = findConflict(request.roomTypeId(), request.startDate(), request.endDate(), request.excludeId())
                .orElse(null);
        return new PriceOverridePreviewResponse(nights.size(), nights, currentTotal, newTotal, conflict);
    }

    private PriceOverride findOrThrow(Long id) {
        return priceOverrideRepository.findById(id).orElseThrow(PriceOverrideNotFoundException::new);
    }

    private RoomType findRoomType(Long roomTypeId) {
        return roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new InvalidPriceOverrideException("Loại phòng không tồn tại, vui lòng chọn lại"));
    }

    /** Ngày kết thúc không được trước ngày bắt đầu; một đợt tối đa 366 đêm. */
    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new InvalidPriceOverrideException("Ngày kết thúc (" + endDate.format(DATE_FORMAT)
                    + ") không được trước ngày bắt đầu (" + startDate.format(DATE_FORMAT) + ")");
        }
        long nights = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (nights > MAX_NIGHTS) {
            throw new InvalidPriceOverrideException("Một đợt giá đè tối đa " + MAX_NIGHTS + " đêm");
        }
    }

    /** AC3: chặn khi lưu nếu trùng ngày với đợt khác của cùng loại phòng. */
    private void checkNoOverlap(PriceOverrideRequest request, Long currentId) {
        findConflict(request.roomTypeId(), request.startDate(), request.endDate(), currentId)
                .ifPresent(message -> {
                    throw new PriceOverrideConflictException(message);
                });
    }

    /**
     * Thông báo nếu có đợt khác của cùng loại phòng có ít nhất một đêm nằm trong khoảng ngày.
     * Bỏ qua chính đợt đang sửa (currentId). Hai đợt liền kề (01/05 và 02/05) không tính là trùng.
     */
    private Optional<String> findConflict(Long roomTypeId, LocalDate startDate, LocalDate endDate, Long currentId) {
        return priceOverrideRepository.findOverlapping(roomTypeId, startDate, endDate)
                .stream()
                .filter(other -> !Objects.equals(other.getId(), currentId))
                .findFirst()
                .map(other -> "Trùng ngày với đợt \"" + other.getName() + "\" ("
                        + other.getStartDate().format(DATE_FORMAT) + " – " + other.getEndDate().format(DATE_FORMAT)
                        + ") của cùng loại phòng. Vui lòng chọn khoảng ngày khác hoặc sửa đợt đó.");
    }

    private void apply(PriceOverride priceOverride, PriceOverrideRequest request, RoomType roomType) {
        priceOverride.setName(request.name().trim());
        priceOverride.setRoomType(roomType);
        priceOverride.setStartDate(request.startDate());
        priceOverride.setEndDate(request.endDate());
        priceOverride.setPricePerNight(request.pricePerNight());
    }
}