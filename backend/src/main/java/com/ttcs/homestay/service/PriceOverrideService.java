package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.pricing.PriceOverrideRequest;
import com.ttcs.homestay.dto.pricing.PriceOverrideResponse;
import com.ttcs.homestay.entity.PriceOverride;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.InvalidPriceOverrideException;
import com.ttcs.homestay.exception.PriceOverrideNotFoundException;
import com.ttcs.homestay.repository.PriceOverrideRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S2-02 Lát 1: thêm, xem, sửa, xoá đợt giá đè theo mùa hoặc ngày lễ.
 * Chặn trùng ngày (AC3), ưu tiên giá (AC2) và xem trước giá (AC4) làm ở các lát sau.
 */
@Service
public class PriceOverrideService {

    /** Một đợt giá đè tối đa 366 đêm (một năm). */
    public static final long MAX_NIGHTS = 366;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PriceOverrideRepository priceOverrideRepository;
    private final RoomTypeRepository roomTypeRepository;

    public PriceOverrideService(PriceOverrideRepository priceOverrideRepository,
            RoomTypeRepository roomTypeRepository) {
        this.priceOverrideRepository = priceOverrideRepository;
        this.roomTypeRepository = roomTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<PriceOverrideResponse> listPriceOverrides() {
        return priceOverrideRepository.findAllByOrderByStartDateAscIdAsc().stream()
                .map(PriceOverrideResponse::from)
                .toList();
    }

    @Transactional
    public PriceOverrideResponse createPriceOverride(PriceOverrideRequest request, String createdByName) {
        validateDates(request);
        PriceOverride priceOverride = new PriceOverride();
        apply(priceOverride, request, findRoomType(request.roomTypeId()));
        OffsetDateTime now = OffsetDateTime.now();
        priceOverride.setCreatedByName(createdByName == null || createdByName.isBlank() ? "Không rõ" : createdByName);
        priceOverride.setCreatedAt(now);
        priceOverride.setUpdatedAt(now);
        return PriceOverrideResponse.from(priceOverrideRepository.save(priceOverride));
    }

    @Transactional
    public PriceOverrideResponse updatePriceOverride(Long id, PriceOverrideRequest request) {
        PriceOverride priceOverride = findOrThrow(id);
        validateDates(request);
        apply(priceOverride, request, findRoomType(request.roomTypeId()));
        priceOverride.setUpdatedAt(OffsetDateTime.now());
        return PriceOverrideResponse.from(priceOverride);
    }

    @Transactional
    public void deletePriceOverride(Long id) {
        priceOverrideRepository.delete(findOrThrow(id));
    }

    private PriceOverride findOrThrow(Long id) {
        return priceOverrideRepository.findById(id).orElseThrow(PriceOverrideNotFoundException::new);
    }

    private RoomType findRoomType(Long roomTypeId) {
        return roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new InvalidPriceOverrideException("Loại phòng không tồn tại, vui lòng chọn lại"));
    }

    /** Ngày kết thúc không được trước ngày bắt đầu; một đợt tối đa 366 đêm. */
    private void validateDates(PriceOverrideRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new InvalidPriceOverrideException("Ngày kết thúc (" + request.endDate().format(DATE_FORMAT)
                    + ") không được trước ngày bắt đầu (" + request.startDate().format(DATE_FORMAT) + ")");
        }
        long nights = ChronoUnit.DAYS.between(request.startDate(), request.endDate()) + 1;
        if (nights > MAX_NIGHTS) {
            throw new InvalidPriceOverrideException("Một đợt giá đè tối đa " + MAX_NIGHTS + " đêm");
        }
    }

    private void apply(PriceOverride priceOverride, PriceOverrideRequest request, RoomType roomType) {
        priceOverride.setName(request.name().trim());
        priceOverride.setRoomType(roomType);
        priceOverride.setStartDate(request.startDate());
        priceOverride.setEndDate(request.endDate());
        priceOverride.setPricePerNight(request.pricePerNight());
    }
}