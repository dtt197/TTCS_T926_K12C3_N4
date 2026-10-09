package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.booking.GuestBookingRequest;
import com.ttcs.homestay.dto.booking.GuestBookingResponse;
import com.ttcs.homestay.dto.booking.WalkInBookingRequest;
import com.ttcs.homestay.dto.booking.GuestQuoteAlternative;
import com.ttcs.homestay.dto.booking.GuestQuoteResponse;
import com.ttcs.homestay.dto.booking.PublicRoomTypeDetailResponse;
import com.ttcs.homestay.dto.booking.PublicRoomTypeOption;
import com.ttcs.homestay.dto.pricing.NightlyPrice;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingSource;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.InvalidGuestBookingException;
import com.ttcs.homestay.exception.RoomTypeNotFoundException;
import com.ttcs.homestay.exception.RoomUnavailableException;
import com.ttcs.homestay.exception.RoomTypeUnavailableException;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * S2-07 Lát 1: khách tự gửi yêu cầu đặt phòng trên trang công khai.
 * Còn phòng thì tạo booking mã 8 ký tự, trạng thái chờ xác nhận, giữ chỗ 24
 * giờ; tổng tiền tính theo S2-02.
 * S2-06: tạm tính và booking dùng chung một cách tính (giá từng đêm + phụ thu
 * thêm người).
 * S2-04: khách xem danh sách loại phòng và chi tiết loại phòng (bộ ảnh, mô tả,
 * tiện nghi, sức chứa, số phòng còn trống).
 */
@Service
public class GuestBookingService {

    /** Giống giới hạn tra phòng trống của S2-05. */
    public static final int MAX_NIGHTS = 30;

    static final Duration HOLD_DURATION = Duration.ofHours(24);
    static final int MAX_CODE_ATTEMPTS = 5;

    private static final ZoneId HOMESTAY_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final BookingRepository bookingRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final RoomAvailabilityService roomAvailabilityService;
    private final PricingService pricingService;
    private final OperatingSettingsService operatingSettingsService;
    private final BookingCodeGenerator bookingCodeGenerator;
    private final BookingRateLimiter bookingRateLimiter;

    @Autowired
    public GuestBookingService(
            BookingRepository bookingRepository,
            RoomTypeRepository roomTypeRepository,
            RoomAvailabilityService roomAvailabilityService,
            PricingService pricingService,
            OperatingSettingsService operatingSettingsService,
            BookingCodeGenerator bookingCodeGenerator,
            BookingRateLimiter bookingRateLimiter) {
        this.bookingRepository = bookingRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.roomAvailabilityService = roomAvailabilityService;
        this.pricingService = pricingService;
        this.operatingSettingsService = operatingSettingsService;
        this.bookingCodeGenerator = bookingCodeGenerator;
        this.bookingRateLimiter = bookingRateLimiter;
    }

    public GuestBookingService(
            BookingRepository bookingRepository,
            RoomTypeRepository roomTypeRepository,
            RoomAvailabilityService roomAvailabilityService,
            PricingService pricingService,
            OperatingSettingsService operatingSettingsService,
            BookingCodeGenerator bookingCodeGenerator) {

        this(
                bookingRepository,
                roomTypeRepository,
                roomAvailabilityService,
                pricingService,
                operatingSettingsService,
                bookingCodeGenerator,
                new BookingRateLimiter());
    }

    /**
     * Loại phòng hiện trong ô chọn: đang bán và đã khai báo đủ giá ngày thường,
     * cuối tuần.
     */
    @Transactional(readOnly = true)
    public List<PublicRoomTypeOption> listBookableRoomTypes() {
        return roomTypeRepository.findAllByOrderByCodeAsc().stream()
                .filter(GuestBookingService::isBookable)
                .map(PublicRoomTypeOption::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicRoomTypeDetailResponse getPublicRoomType(Long roomTypeId) {
        LocalDate today = LocalDate.now(HOMESTAY_ZONE);
        LocalDate tomorrow = today.plusDays(1);
        RoomType roomType = roomTypeRepository.findById(roomTypeId)
                .orElseThrow(() -> new RoomTypeNotFoundException(
                        "Không tìm thấy thông tin loại phòng #" + roomTypeId));
        if (!Boolean.TRUE.equals(roomType.getStatus())) {
            throw new RoomTypeUnavailableException();
        }
        int available = roomAvailabilityService.availableRooms(roomType, today, tomorrow);
        OperatingSettings settings = operatingSettingsService.findEffectiveAt(OffsetDateTime.now());
        return PublicRoomTypeDetailResponse.from(
                roomType, available, roomAvailabilityService.activeRoomCount(roomType), settings);
    }

    /**
     * S2-04: Danh sách loại phòng đầy đủ chi tiết (ảnh, tiện nghi, số phòng trống)
     * cho khách xem danh mục phòng công khai.
     */
    @Transactional(readOnly = true)
    public List<PublicRoomTypeDetailResponse> listBookableRoomTypesWithDetails() {
        LocalDate today = LocalDate.now(HOMESTAY_ZONE);
        LocalDate tomorrow = today.plusDays(1);
        OperatingSettings settings = operatingSettingsService.findEffectiveAt(OffsetDateTime.now());
        return roomTypeRepository.findAllByOrderByCodeAsc().stream()
                .filter(GuestBookingService::isBookable)
                .map(rt -> {
                    int available = roomAvailabilityService.availableRooms(rt, today, tomorrow);
                    return PublicRoomTypeDetailResponse.from(
                            rt, available, roomAvailabilityService.activeRoomCount(rt), settings);
                })
                .toList();
    }

    /**
     * S2-04: Lấy chi tiết một loại phòng cho khách xem kèm đầy đủ chính sách nhận/trả phòng,
     * trẻ nhỏ, phụ thu thêm người và mốc phí hủy.
     * Ném RoomTypeNotFoundException nếu không tìm thấy hoặc phòng ngừng bán.
     */
    @Transactional(readOnly = true)
    public PublicRoomTypeDetailResponse getRoomTypeDetail(Long id) {
        LocalDate today = LocalDate.now(HOMESTAY_ZONE);
        LocalDate tomorrow = today.plusDays(1);
        RoomType roomType = roomTypeRepository.findById(id)
                .filter(GuestBookingService::isBookable)
                .orElseThrow(() -> new RoomTypeNotFoundException(
                        "Không tìm thấy thông tin loại phòng #" + id));
        int available = roomAvailabilityService.availableRooms(roomType, today, tomorrow);
        OperatingSettings settings = operatingSettingsService.findEffectiveAt(OffsetDateTime.now());
        return PublicRoomTypeDetailResponse.from(
                roomType, available, roomAvailabilityService.activeRoomCount(roomType), settings);
    }

    @Transactional
    public GuestBookingResponse createGuestBooking(GuestBookingRequest request) {
        return createGuestBooking(request, null);
    }

    @Transactional
    public GuestBookingResponse createGuestBooking(
            GuestBookingRequest request,
            String ipAddress) {

        if (!bookingRateLimiter.allow(ipAddress)) {
            throw new InvalidGuestBookingException(
                    "Bạn đã gửi quá nhiều yêu cầu đặt phòng. Vui lòng thử lại sau.");
        }

        validateDates(request.checkInDate(), request.checkOutDate());

        RoomType roomType = roomTypeRepository
                .findByIdForUpdate(request.roomTypeId())
                .filter(GuestBookingService::isBookable)
                .orElseThrow(() -> new InvalidGuestBookingException(
                        "Loại phòng không tồn tại hoặc đã ngừng bán, vui lòng chọn lại"));
        if (request.guestCount() > roomType.getMaxCapacity()) {
            throw new InvalidGuestBookingException(roomType.getName() + " chỉ nhận tối đa "
                    + roomType.getMaxCapacity() + " khách, bạn đang chọn " + request.guestCount() + " khách");
        }
        if (roomAvailabilityService.availableRooms(roomType, request.checkInDate(), request.checkOutDate()) < 1) {
            throw RoomAvailabilityService.unavailable(roomType, request.checkInDate(), request.checkOutDate());
        }

        OffsetDateTime createdAt = OffsetDateTime.now();
        OperatingSettings settings = operatingSettingsService.findEffectiveAt(createdAt);
        GuestQuoteResponse price = calculate(roomType, request.checkInDate(), request.checkOutDate(),
                request.guestCount(), settings);

        Booking booking = new Booking();
        booking.setBookingCode(newUniqueCode());
        booking.setStatus(BookingStatus.CHO_XAC_NHAN);
        booking.setSource(BookingSource.TRUC_TUYEN);
        booking.setGuestName(request.guestName().trim());
        booking.setGuestPhone(request.phone().trim());
        booking.setGuestEmail(request.email().trim().toLowerCase(Locale.ROOT));
        booking.setGuestCount(request.guestCount());
        booking.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        booking.setRoomType(roomType);
        booking.setRoomTypeNameSnapshot(roomType.getName());
        booking.setCheckInDate(request.checkInDate());
        booking.setCheckOutDate(request.checkOutDate());
        booking.setWeekdayPriceSnapshot(roomType.getWeekdayPrice());
        booking.setWeekendPriceSnapshot(roomType.getWeekendPrice());
        booking.setWeekendDaysSnapshot(settings.getWeekendDays());
        booking.setExtraGuestCount(price.extraGuests());
        booking.setExtraPersonFeeSnapshot(price.extraPersonFee());
        booking.setSurchargeAmount(price.surchargeAmount());
        booking.setTotalAmount(price.totalAmount());
        booking.setCreatedAt(createdAt);
        booking.setHoldExpiresAt(createdAt.plus(HOLD_DURATION));
        // S3-03: giữ một suất theo loại phòng; lễ tân gán phòng cụ thể sau khi xác nhận.

        return GuestBookingResponse.from(saveHoldingRoom(booking, roomType));
    }

    /** S3-02: lưu booking đang giữ phòng; cơ sở dữ liệu từ chối do trùng phòng cùng đêm thì báo hết phòng (409). */
    private Booking saveHoldingRoom(Booking booking, RoomType roomType) {
        try {
            booking.setRoom(roomAvailabilityService.assignRoom(
                    roomType, booking.getCheckInDate(), booking.getCheckOutDate(), null, null));
            Booking saved = bookingRepository.save(booking);
            bookingRepository.flush();
            return saved;
        } catch (DataIntegrityViolationException exception) {
            if (RoomAvailabilityService.isRoomOverlapViolation(exception)) {
                throw RoomAvailabilityService.unavailable(roomType, booking.getCheckInDate(), booking.getCheckOutDate());
            }
            throw exception;
        }
    }

    @Transactional
    public GuestBookingResponse createWalkInBooking(
            WalkInBookingRequest request) {

        validateDates(request.checkInDate(), request.checkOutDate());

        if (request.guestCount() < 1) {
            throw new InvalidGuestBookingException("Số khách ít nhất là 1");
        }

        RoomType roomType = roomTypeRepository
                .findByIdForUpdate(request.roomTypeId())
                .filter(GuestBookingService::isBookable)
                .orElseThrow(() -> new InvalidGuestBookingException(
                        "Loại phòng không tồn tại hoặc đã ngừng bán, vui lòng chọn lại"));

        if (request.guestCount() > roomType.getMaxCapacity()) {
            throw new InvalidGuestBookingException(
                    roomType.getName() + " chỉ nhận tối đa "
                            + roomType.getMaxCapacity()
                            + " khách, bạn đang chọn "
                            + request.guestCount() + " khách");
        }

        if (roomAvailabilityService.availableRooms(
                roomType,
                request.checkInDate(),
                request.checkOutDate()) < 1) {

            throw RoomAvailabilityService.unavailable(roomType, request.checkInDate(), request.checkOutDate());
        }

        OffsetDateTime createdAt = OffsetDateTime.now();

        OperatingSettings settings = operatingSettingsService.findEffectiveAt(createdAt);

        GuestQuoteResponse price = calculate(
                roomType,
                request.checkInDate(),
                request.checkOutDate(),
                request.guestCount(),
                settings);

        Booking booking = new Booking();

        booking.setBookingCode(newUniqueCode());

        // Booking tại quầy xác nhận ngay
        booking.setStatus(BookingStatus.DA_XAC_NHAN);

        booking.setSource(BookingSource.TRUC_TUYEN);

        // Nguồn booking: tại quầy
        booking.setSource(BookingSource.TAI_QUAY);

        booking.setGuestName(request.guestName().trim());

        booking.setGuestPhone(
                request.phone() == null ? null : request.phone().trim());

        booking.setGuestEmail(
                request.email() == null || request.email().isBlank()
                        ? null
                        : request.email().trim().toLowerCase(Locale.ROOT));

        booking.setGuestCount(request.guestCount());

        booking.setNote(
                request.note() == null || request.note().isBlank()
                        ? null
                        : request.note().trim());

        booking.setRoomType(roomType);

        booking.setRoomTypeNameSnapshot(roomType.getName());

        booking.setCheckInDate(request.checkInDate());

        booking.setCheckOutDate(request.checkOutDate());

        booking.setWeekdayPriceSnapshot(roomType.getWeekdayPrice());

        booking.setWeekendPriceSnapshot(roomType.getWeekendPrice());

        booking.setWeekendDaysSnapshot(settings.getWeekendDays());

        booking.setExtraGuestCount(price.extraGuests());

        booking.setExtraPersonFeeSnapshot(price.extraPersonFee());

        booking.setSurchargeAmount(price.surchargeAmount());

        booking.setTotalAmount(price.totalAmount());

        booking.setCreatedAt(createdAt);

        // Booking tại quầy đã xác nhận nên không giữ chỗ 24 giờ
        booking.setHoldExpiresAt(null);

        // S3-03: booking tại quầy cũng giữ một suất theo loại phòng, chưa gán phòng cụ thể.

        return GuestBookingResponse.from(saveHoldingRoom(booking, roomType));
    }

    /**
     * S2-06: giá tạm tính từng đêm và phụ thu thêm người cho khách xem trước khi
     * gửi yêu cầu (không lưu gì).
     */
    @Transactional(readOnly = true)
    public GuestQuoteResponse quote(Long roomTypeId, LocalDate checkIn, LocalDate checkOut, int guestCount) {
        validateDates(checkIn, checkOut);
        if (guestCount < 1) {
            throw new InvalidGuestBookingException("Số khách ít nhất là 1");
        }
        RoomType roomType = findBookableRoomType(roomTypeId);
        OperatingSettings settings = operatingSettingsService.findEffectiveAt(OffsetDateTime.now());
        List<GuestQuoteAlternative> alternatives = guestCount > roomType.getMaxCapacity()
                ? findAlternatives(roomType, checkIn, checkOut, guestCount, settings)
                : List.of();
        return calculate(roomType, checkIn, checkOut, guestCount, settings, alternatives);
    }

    private List<GuestQuoteAlternative> findAlternatives(
            RoomType currentRoomType, LocalDate checkIn, LocalDate checkOut,
            int guestCount, OperatingSettings settings) {
        List<GuestQuoteAlternative> alternatives = new ArrayList<>();
        for (RoomType candidate : roomTypeRepository.findAllByOrderByCodeAsc()) {
            if (candidate.getId().equals(currentRoomType.getId())
                    || candidate.getMaxCapacity() < guestCount
                    || !isBookable(candidate)
                    || roomAvailabilityService.availableRooms(candidate, checkIn, checkOut) < 1) {
                continue;
            }
            GuestQuoteResponse quote = calculate(candidate, checkIn, checkOut, guestCount, settings);
            alternatives.add(new GuestQuoteAlternative(
                    candidate.getId(), candidate.getName(), candidate.getMaxCapacity(), quote.totalAmount()));
        }
        return List.copyOf(alternatives);
    }

    /**
     * Cách tính dùng chung cho tạm tính và booking: tổng giá các đêm (S2-02)
     * + số người vượt sức chứa tiêu chuẩn × mức phụ thu thêm người × số đêm.
     */
    private GuestQuoteResponse calculate(
            RoomType roomType, LocalDate checkIn, LocalDate checkOut, int guestCount, OperatingSettings settings) {
        return calculate(roomType, checkIn, checkOut, guestCount, settings, List.of());
    }

    private GuestQuoteResponse calculate(
            RoomType roomType, LocalDate checkIn, LocalDate checkOut, int guestCount,
            OperatingSettings settings, List<GuestQuoteAlternative> alternatives) {
        List<NightlyPrice> nightlyPrices = pricingService.priceNights(roomType, checkIn, checkOut,
                PricingService.parseWeekendDays(settings.getWeekendDays()));
        long nightsTotal = PricingService.total(nightlyPrices);
        int extraGuests = Math.max(0, guestCount - roomType.getStandardCapacity());
        int minimumRooms = (guestCount - 1) / roomType.getMaxCapacity() + 1;
        long extraPersonFee = roomType.getExtraGuestFee() == null ? 0L : roomType.getExtraGuestFee();
        long surchargeAmount = Math.multiplyExact(
                Math.multiplyExact((long) extraGuests, extraPersonFee), nightlyPrices.size());
        return new GuestQuoteResponse(
                roomType.getId(),
                roomType.getName(),
                checkIn,
                checkOut,
                nightlyPrices.size(),
                nightlyPrices,
                nightsTotal,
                guestCount,
                roomType.getStandardCapacity(),
                roomType.getMaxCapacity(),
                guestCount > roomType.getMaxCapacity(),
                minimumRooms,
                alternatives,
                extraGuests,
                extraPersonFee,
                surchargeAmount,
                Math.addExact(nightsTotal, surchargeAmount));
    }

    private RoomType findBookableRoomType(Long roomTypeId) {
        return roomTypeRepository.findById(roomTypeId)
                .filter(GuestBookingService::isBookable)
                .orElseThrow(() -> new InvalidGuestBookingException(
                        "Loại phòng không tồn tại hoặc đã ngừng bán, vui lòng chọn lại"));
    }

    /**
     * Ngày trả sau ngày nhận ít nhất một đêm, không nhận phòng ở quá khứ, tối đa 30
     * đêm.
     */
    private static void validateDates(LocalDate checkIn, LocalDate checkOut) {
        if (!checkOut.isAfter(checkIn)) {
            throw new InvalidGuestBookingException("Ngày trả phòng phải sau ngày nhận phòng ít nhất một đêm");
        }
        LocalDate today = PublicBookingDatePolicy.today();
        if (checkIn.isBefore(today)) {
            throw new InvalidGuestBookingException("Ngày nhận phòng không được ở trong quá khứ");
        }
        if (PublicBookingDatePolicy.isCheckInTooFar(checkIn, today)) {
            throw new InvalidGuestBookingException(PublicBookingDatePolicy.CHECK_IN_TOO_FAR_MESSAGE);
        }
        if (ChronoUnit.DAYS.between(checkIn, checkOut) > MAX_NIGHTS) {
            throw new InvalidGuestBookingException("Mỗi lần đặt tối đa " + MAX_NIGHTS + " đêm");
        }
    }

    /**
     * Mã ngẫu nhiên, nếu đã có booking dùng mã đó thì sinh lại (cột booking_code
     * cũng có ràng buộc UNIQUE).
     */
    private String newUniqueCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = bookingCodeGenerator.next();
            if (!bookingRepository.existsByBookingCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Không sinh được mã booking, vui lòng thử lại");
    }

    private static boolean isBookable(RoomType roomType) {
        return Boolean.TRUE.equals(roomType.getStatus())
                && roomType.getWeekdayPrice() != null && roomType.getWeekdayPrice() > 0
                && roomType.getWeekendPrice() != null && roomType.getWeekendPrice() > 0;
    }
}
