package com.ttcs.homestay.service;

import java.util.Locale;
import java.util.UUID;

import com.ttcs.homestay.dto.booking.BookingAuditLogResponse;
import com.ttcs.homestay.dto.booking.BookingChangePreviewResponse;
import com.ttcs.homestay.dto.booking.BookingCreateRequest;
import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.dto.booking.BookingUpdateRequest;
import com.ttcs.homestay.dto.pricing.NightlyPrice;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingAuditLog;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.exception.RoomTypeNotFoundException;
import com.ttcs.homestay.exception.RoomUnavailableException;
import com.ttcs.homestay.repository.BookingAuditLogRepository;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import com.ttcs.homestay.dto.booking.BookingConfirmResponse;
import com.ttcs.homestay.dto.booking.BookingListItemResponse;
import com.ttcs.homestay.dto.booking.BookingConfirmRequest;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.time.LocalDate;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final OperatingSettingsService operatingSettingsService;
    private final PricingService pricingService;
    private final BookingDepositService bookingDepositService;
    private final AuditLogService auditLogService;
    private final RoomAvailabilityService roomAvailabilityService;
    private final BookingAuditLogRepository bookingAuditLogRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public BookingService(
            BookingRepository bookingRepository,
            RoomTypeRepository roomTypeRepository,
            OperatingSettingsService operatingSettingsService,
            PricingService pricingService,
            BookingDepositService bookingDepositService,
            AuditLogService auditLogService,
            RoomAvailabilityService roomAvailabilityService,
            BookingAuditLogRepository bookingAuditLogRepository) {
        this.bookingRepository = bookingRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.operatingSettingsService = operatingSettingsService;
        this.pricingService = pricingService;
        this.bookingDepositService = bookingDepositService;
        this.auditLogService = auditLogService;
        this.roomAvailabilityService = roomAvailabilityService;
        this.bookingAuditLogRepository = bookingAuditLogRepository;
    }

    public BookingService(
            BookingRepository bookingRepository,
            RoomTypeRepository roomTypeRepository,
            OperatingSettingsService operatingSettingsService,
            PricingService pricingService,
            BookingDepositService bookingDepositService,
            AuditLogService auditLogService,
            RoomAvailabilityService roomAvailabilityService) {
        this(bookingRepository, roomTypeRepository, operatingSettingsService, pricingService, bookingDepositService,
                auditLogService, roomAvailabilityService, null);
    }

    @Transactional
    public BookingConfirmResponse confirmBooking(Long id, BookingConfirmRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long actorId = null;
        String actorEmail = null;
        String actorName = null;
        if (authentication instanceof JwtAuthenticationToken token) {
            try {
                actorId = Long.valueOf(token.getToken().getSubject());
            } catch (NumberFormatException ignored) {
                // The JWT subject may not be a numeric user ID.
            }
            actorEmail = token.getToken().getClaimAsString("email");
            actorName = token.getToken().getClaimAsString("fullName");
        }
        if ((actorEmail == null || actorEmail.isBlank()) && authentication != null) {
            actorEmail = authentication.getName();
        }
        if ((actorName == null || actorName.isBlank()) && authentication != null) {
            actorName = authentication.getName();
        }
        BookingConfirmResponse response = bookingDepositService.confirmBooking(id, request, actorName);
        String ipAddress = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            ipAddress = attrs.getRequest().getRemoteAddr();
        }
        // Join the booking/deposit transaction: audit failure must roll back confirmation.
        // user_id references users, so identify the booking in the existing target label instead.
        auditLogService.recordSensitiveAction(
                actorId, actorEmail, null, "Booking " + response.bookingCode(),
                "BOOKING_CONFIRMED", ipAddress);
        return response;
    }

    @Transactional
    public BookingResponse createBooking(BookingCreateRequest request) {
        if (!request.checkOutDate().isAfter(request.checkInDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày trả phòng phải sau ngày nhận phòng");
        }

        RoomType roomType = roomTypeRepository.findById(request.roomTypeId())
                .orElseThrow(RoomTypeNotFoundException::new);
        if (!Boolean.TRUE.equals(roomType.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Loại phòng đã ngừng bán");
        }
        Long weekdayPrice = roomType.getWeekdayPrice();
        Long weekendPrice = roomType.getWeekendPrice();
        if (weekdayPrice == null || weekdayPrice <= 0 || weekendPrice == null || weekendPrice <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Loại phòng cần có giá ngày thường và giá cuối tuần hợp lệ");
        }

        OffsetDateTime createdAt = OffsetDateTime.now();
        OperatingSettings settings = operatingSettingsService.findEffectiveAt(createdAt);
        Set<DayOfWeek> weekendDays = PricingService.parseWeekendDays(settings.getWeekendDays());

        long totalAmount = PricingService.total(pricingService.priceNights(
                roomType, request.checkInDate(), request.checkOutDate(), weekendDays));

        Booking booking = new Booking();

        booking.setBookingCode(
                "BK-" + UUID.randomUUID()
                        .toString()
                        .replace("-", "")
                        .substring(0, 8)
                        .toUpperCase(Locale.ROOT)
        );

        booking.setGuestName(request.guestName().trim());
        booking.setStatus(BookingStatus.CHO_XAC_NHAN);

        booking.setRoomType(roomType);
        booking.setRoomTypeNameSnapshot(roomType.getName());
        booking.setCheckInDate(request.checkInDate());
        booking.setCheckOutDate(request.checkOutDate());
        booking.setWeekdayPriceSnapshot(weekdayPrice);
        booking.setWeekendPriceSnapshot(weekendPrice);
        booking.setWeekendDaysSnapshot(settings.getWeekendDays());
        booking.setTotalAmount(totalAmount);
        booking.setCreatedAt(createdAt);

        Booking savedBooking = bookingRepository.save(booking);

        return new BookingResponse(
            savedBooking.getId(),
            savedBooking.getBookingCode(),
            savedBooking.getGuestName(),
            roomType.getId(),
            savedBooking.getRoomTypeNameSnapshot(),
            savedBooking.getCheckInDate(),
            savedBooking.getCheckOutDate(),
            savedBooking.getWeekdayPriceSnapshot(),
            savedBooking.getWeekendPriceSnapshot(),
            savedBooking.getWeekendDaysSnapshot(),
            savedBooking.getTotalAmount(),
            savedBooking.getStatus(),
            savedBooking.getCreatedAt(),
            BookingHoldPolicy.isExpired(
                savedBooking.getStatus(),
                savedBooking.getCreatedAt() != null ? savedBooking.getCreatedAt().toInstant() : null,
                java.time.Instant.now()
            )
        );
    }

    @Transactional(readOnly = true)
    public BookingChangePreviewResponse previewBookingChange(Long id, BookingUpdateRequest request) {
        validateUpdateRequest(request);

        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy booking"));

        RoomType roomType = roomTypeRepository.findById(request.roomTypeId())
                .orElseThrow(RoomTypeNotFoundException::new);

        if (!Boolean.TRUE.equals(roomType.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Loại phòng đã ngừng bán");
        }

        int availableRooms = roomAvailabilityService.availableRooms(
                roomType, request.checkInDate(), request.checkOutDate(), booking.getId());

        OperatingSettings settings = operatingSettingsService.findEffectiveAt(OffsetDateTime.now());
        Set<DayOfWeek> weekendDays = PricingService.parseWeekendDays(settings.getWeekendDays());

        List<NightlyPrice> nightlyPrices = pricingService.priceNights(
                roomType, request.checkInDate(), request.checkOutDate(), weekendDays);
        long totalAmount = PricingService.total(nightlyPrices);
        int numberOfNights = nightlyPrices.size();

        return new BookingChangePreviewResponse(
                booking.getId(),
                roomType.getId(),
                roomType.getName(),
                request.checkInDate(),
                request.checkOutDate(),
                numberOfNights,
                totalAmount,
                availableRooms,
                availableRooms > 0,
                nightlyPrices
        );
    }

    public record ActorInfo(Long id, String name, String email) {}

    public ActorInfo resolveCurrentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long actorId = null;
        String actorEmail = null;
        String actorName = null;
        if (authentication instanceof JwtAuthenticationToken token) {
            try {
                actorId = Long.valueOf(token.getToken().getSubject());
            } catch (NumberFormatException ignored) {
                // The JWT subject may not be a numeric user ID.
            }
            actorEmail = token.getToken().getClaimAsString("email");
            actorName = token.getToken().getClaimAsString("fullName");
        }
        if ((actorEmail == null || actorEmail.isBlank()) && authentication != null) {
            actorEmail = authentication.getName();
        }
        if ((actorName == null || actorName.isBlank()) && authentication != null) {
            actorName = authentication.getName();
        }
        if (actorName == null || actorName.isBlank()) {
            actorName = "Hệ thống";
        }
        return new ActorInfo(actorId, actorName, actorEmail);
    }

    @Transactional
    public BookingResponse updateBooking(Long id, BookingUpdateRequest request) {
        return updateBooking(id, request, resolveCurrentActor());
    }

    @Transactional
    public BookingResponse updateBooking(Long id, BookingUpdateRequest request, ActorInfo actor) {
        validateUpdateRequest(request);

        Booking booking = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Không tìm thấy booking"));
        if (bookingDepositService.hasDeposit(booking)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Booking có tiền cọc đã ghi nhận, không thể cập nhật trực tiếp");
        }

        RoomType roomType = roomTypeRepository.findById(request.roomTypeId())
                .orElseThrow(RoomTypeNotFoundException::new);

        if (!Boolean.TRUE.equals(roomType.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Loại phòng đã ngừng bán");
        }

        // 1. Kiểm tra tính khả dụng (room availability)
        int availableRooms = roomAvailabilityService.availableRooms(
                roomType, request.checkInDate(), request.checkOutDate(), booking.getId());
        if (availableRooms <= 0) {
            throw new RoomUnavailableException(
                    "Loại phòng " + roomType.getName() + " đã hết phòng trống trong khoảng thời gian đã chọn");
        }

        // 2. Tính lại tiền phòng theo khoảng ngày và loại phòng mới
        OperatingSettings settings = operatingSettingsService.findEffectiveAt(OffsetDateTime.now());
        Set<DayOfWeek> weekendDays = PricingService.parseWeekendDays(settings.getWeekendDays());

        List<NightlyPrice> nightlyPrices = pricingService.priceNights(
                roomType, request.checkInDate(), request.checkOutDate(), weekendDays);
        long newTotalAmount = PricingService.total(nightlyPrices);

        // Capture previous state for audit log / change history
        LocalDate oldCheckInDate = booking.getCheckInDate();
        LocalDate oldCheckOutDate = booking.getCheckOutDate();
        Long oldRoomTypeId = booking.getRoomType() != null ? booking.getRoomType().getId() : null;
        String oldRoomTypeName = booking.getRoomTypeNameSnapshot();
        long oldTotalAmount = booking.getTotalAmount();

        // 3. Cập nhật booking cũ
        booking.setRoomType(roomType);
        booking.setRoomTypeNameSnapshot(roomType.getName());
        booking.setCheckInDate(request.checkInDate());
        booking.setCheckOutDate(request.checkOutDate());
        booking.setTotalAmount(newTotalAmount);
        if (roomType.getWeekdayPrice() != null && roomType.getWeekdayPrice() > 0) {
            booking.setWeekdayPriceSnapshot(roomType.getWeekdayPrice());
        }
        if (roomType.getWeekendPrice() != null && roomType.getWeekendPrice() > 0) {
            booking.setWeekendPriceSnapshot(roomType.getWeekendPrice());
        }
        booking.setWeekendDaysSnapshot(settings.getWeekendDays());

        Booking savedBooking = bookingRepository.save(booking);

        // 4. Ghi nhận Booking Audit Log / History trong cùng Database Transaction
        if (bookingAuditLogRepository != null) {
            ActorInfo effectiveActor = actor != null ? actor : resolveCurrentActor();
            BookingAuditLog auditLog = new BookingAuditLog();
            auditLog.setBooking(savedBooking);
            auditLog.setBookingCode(savedBooking.getBookingCode());
            auditLog.setOldCheckInDate(oldCheckInDate);
            auditLog.setNewCheckInDate(request.checkInDate());
            auditLog.setOldCheckOutDate(oldCheckOutDate);
            auditLog.setNewCheckOutDate(request.checkOutDate());
            auditLog.setOldRoomTypeId(oldRoomTypeId);
            auditLog.setOldRoomTypeName(oldRoomTypeName != null ? oldRoomTypeName : "");
            auditLog.setNewRoomTypeId(roomType.getId());
            auditLog.setNewRoomTypeName(roomType.getName());
            auditLog.setOldTotalAmount(oldTotalAmount);
            auditLog.setNewTotalAmount(newTotalAmount);
            auditLog.setActorUserId(effectiveActor.id());
            auditLog.setActorName(effectiveActor.name());
            auditLog.setActorEmail(effectiveActor.email());
            auditLog.setCreatedAt(OffsetDateTime.now());

            bookingAuditLogRepository.save(auditLog);
        }

        return BookingResponse.from(savedBooking);
    }

    @Transactional(readOnly = true)
    public List<BookingAuditLogResponse> getBookingHistory(Long bookingId) {
        if (!bookingRepository.existsById(bookingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy booking");
        }
        if (bookingAuditLogRepository == null) {
            return List.of();
        }
        return bookingAuditLogRepository.findByBookingIdOrderByCreatedAtDescIdDesc(bookingId)
                .stream()
                .map(BookingAuditLogResponse::from)
                .toList();
    }

    private void validateUpdateRequest(BookingUpdateRequest request) {
        if (request == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Yêu cầu không được để trống");
        }

        if (request.roomTypeId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Loại phòng không được để trống");
        }

        if (request.checkInDate() == null
                || request.checkOutDate() == null
                || !request.checkOutDate().isAfter(request.checkInDate())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Ngày trả phòng phải sau ngày nhận phòng");
        }
    }

    @Transactional(readOnly = true)
    public List<BookingListItemResponse> getLatestBookings() {
        return bookingRepository.findAllByOrderByCreatedAtDescIdDesc()
                .stream()
                .map(BookingListItemResponse::from)
                .toList();
    }

    public static final int PAGE_SIZE = 20;

    @Transactional(readOnly = true)
    public Page<Booking> findPage(
        int page,
        BookingStatus status,
        LocalDate checkInFrom,
        LocalDate checkInTo,
        String keyword) {

    String normalizedKeyword =
            keyword == null || keyword.trim().isEmpty()
                    ? null
                    : keyword.trim();

    Pageable pageable = PageRequest.of(
            Math.max(page, 0),
            PAGE_SIZE,
            Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.desc("id")
            )
    );

    return bookingRepository.search(
            status,
            checkInFrom,
            checkInTo,
            normalizedKeyword,
            pageable
    );
}

    @Transactional(readOnly = true)
    public Page<Booking> findPage(int page) {
    return findPage(page, null, null, null, null);
}
}
