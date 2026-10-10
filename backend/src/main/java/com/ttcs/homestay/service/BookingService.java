package com.ttcs.homestay.service;

import java.util.Locale;
import java.util.UUID;

import com.ttcs.homestay.dto.booking.BookingAuditLogResponse;
import com.ttcs.homestay.dto.booking.BookingChangePreviewResponse;
import com.ttcs.homestay.dto.booking.BookingCancelRequest;
import com.ttcs.homestay.dto.booking.BookingCreateRequest;
import com.ttcs.homestay.dto.booking.BookingResponse;
import com.ttcs.homestay.dto.booking.CheckInOptionResponse;
import com.ttcs.homestay.dto.booking.BookingUpdateRequest;
import com.ttcs.homestay.dto.pricing.NightlyPrice;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingAuditLog;
import com.ttcs.homestay.entity.BookingRoomChangeHistory;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.RoomType;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.exception.RoomTypeNotFoundException;
import com.ttcs.homestay.exception.RoomUnavailableException;
import com.ttcs.homestay.repository.BookingAuditLogRepository;
import com.ttcs.homestay.repository.BookingRoomChangeHistoryRepository;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoomTypeRepository;
import com.ttcs.homestay.repository.RoomRepository;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
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
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import java.time.LocalDate;

@Service
public class BookingService {

    @PersistenceContext
    private EntityManager entityManager;

    private final BookingRepository bookingRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final OperatingSettingsService operatingSettingsService;
    private final PricingService pricingService;
    private final BookingDepositService bookingDepositService;
    private final AuditLogService auditLogService;
    private final RoomAvailabilityService roomAvailabilityService;
    private final BookingAuditLogRepository bookingAuditLogRepository;
    private final RoomRepository roomRepository;
    private final BookingRoomChangeHistoryRepository roomChangeHistoryRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public BookingService(
            BookingRepository bookingRepository,
            RoomTypeRepository roomTypeRepository,
            OperatingSettingsService operatingSettingsService,
            PricingService pricingService,
            BookingDepositService bookingDepositService,
            AuditLogService auditLogService,
            RoomAvailabilityService roomAvailabilityService,
            BookingAuditLogRepository bookingAuditLogRepository,
            RoomRepository roomRepository,
            BookingRoomChangeHistoryRepository roomChangeHistoryRepository) {
        this.bookingRepository = bookingRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.operatingSettingsService = operatingSettingsService;
        this.pricingService = pricingService;
        this.bookingDepositService = bookingDepositService;
        this.auditLogService = auditLogService;
        this.roomAvailabilityService = roomAvailabilityService;
        this.bookingAuditLogRepository = bookingAuditLogRepository;
        this.roomRepository = roomRepository;
        this.roomChangeHistoryRepository = roomChangeHistoryRepository;
    }

    public BookingService(
            BookingRepository bookingRepository,
            RoomTypeRepository roomTypeRepository,
            OperatingSettingsService operatingSettingsService,
            PricingService pricingService,
            BookingDepositService bookingDepositService,
            AuditLogService auditLogService,
            RoomAvailabilityService roomAvailabilityService,
            BookingAuditLogRepository bookingAuditLogRepository,
            RoomRepository roomRepository) {
        this(bookingRepository, roomTypeRepository, operatingSettingsService, pricingService, bookingDepositService,
                auditLogService, roomAvailabilityService, bookingAuditLogRepository, roomRepository, null);
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
                auditLogService, roomAvailabilityService, null, null, null);
    }

    @Transactional
    public BookingResponse changeConfirmedRoom(Long bookingId, Long roomId, String rawReason) {
        if (roomChangeHistoryRepository == null) {
            throw new IllegalStateException("BookingRoomChangeHistoryRepository is required for room changes");
        }
        String reason = rawReason == null ? "" : rawReason.strip();
        if (reason.codePoints().allMatch(value -> Character.isWhitespace(value) || Character.isSpaceChar(value))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do đổi phòng");
        }
        if (reason.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lý do đổi phòng không được vượt quá 500 ký tự");
        }
        Booking snapshot = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy booking"));
        if (snapshot.getRoomType() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking không có loại phòng hợp lệ");
        }
        roomTypeRepository.findByIdForUpdate(snapshot.getRoomType().getId());
        Room requestedRoom = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng"));
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy booking"));
        entityManager.refresh(booking);
        boolean checkedIn = booking.getStatus() == BookingStatus.DA_NHAN_PHONG;
        LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
        if (!checkedIn && (booking.getStatus() != BookingStatus.DA_XAC_NHAN || booking.getRoomConfirmedAt() == null
                || booking.getCheckInDate() == null || booking.getCheckInDate().isBefore(LocalDate.now()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ đổi phòng cho booking đang ở hoặc đã xác nhận, đã chốt phòng và chưa qua ngày nhận phòng");
        }
        Room oldRoom = booking.getRoom();
        if (oldRoom == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking chưa có phòng đã chốt");
        }
        if (oldRoom.getId().equals(requestedRoom.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Phòng mới phải khác phòng hiện tại");
        }
        ActorInfo actor = resolveCurrentActor();
        if (actor.id() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không xác định được nhân viên đổi phòng");
        }
        Room lockedOldRoom;
        Room lockedNewRoom;
        if (oldRoom.getId() < requestedRoom.getId()) {
            lockedOldRoom = lockRoomForChange(oldRoom.getId());
            lockedNewRoom = lockRoomForChange(requestedRoom.getId());
        } else {
            lockedNewRoom = lockRoomForChange(requestedRoom.getId());
            lockedOldRoom = lockRoomForChange(oldRoom.getId());
        }
        entityManager.refresh(lockedOldRoom);
        entityManager.refresh(lockedNewRoom);
        if (!checkedIn && !roomAvailabilityService.isRoomAvailable(lockedNewRoom, booking.getRoomType(),
                booking.getCheckInDate(), booking.getCheckOutDate(), booking.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Phòng mới sai loại, đang bảo trì hoặc đã bị booking khác chiếm trong thời gian lưu trú");
        }
        if (checkedIn) {
            if (lockedOldRoom.getStatus() != RoomStatus.DANG_O) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Phòng hiện tại phải ở trạng thái Đang ở");
            }
            if (lockedNewRoom.getStatus() != RoomStatus.TRONG_SACH) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Phòng mới phải ở trạng thái Trống sạch");
            }
            LocalDate remainingStart = remainingStayStart(booking, today);
            if (!roomAvailabilityService.isRoomAvailable(lockedNewRoom, booking.getRoomType(),
                    remainingStart, booking.getCheckOutDate(), booking.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Phòng mới sai loại, ngừng hoạt động, bảo trì hoặc trùng lịch trong thời gian ở còn lại");
            }
            if (!roomAvailabilityService.satisfiesRoomOverlapConstraint(lockedNewRoom, booking)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Phòng mới vướng lịch trong toàn bộ kỳ lưu trú. Giới hạn hiện tại yêu cầu không trùng lịch cả phần ngày đã qua");
            }
            lockedOldRoom.setStatus(RoomStatus.TRONG_BAN);
            lockedNewRoom.setStatus(RoomStatus.DANG_O);
        }
        booking.setRoom(lockedNewRoom);
        BookingRoomChangeHistory history = new BookingRoomChangeHistory();
        history.setBooking(booking);
        history.setOldRoom(lockedOldRoom);
        history.setNewRoom(lockedNewRoom);
        history.setActor(entityManager.getReference(com.ttcs.homestay.entity.User.class, actor.id()));
        history.setChangedAt(OffsetDateTime.now());
        history.setReason(reason);
        try {
            Booking saved = bookingRepository.save(booking);
            bookingRepository.flush();
            roomChangeHistoryRepository.save(history);
            roomChangeHistoryRepository.flush();
            return BookingResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            if (RoomAvailabilityService.isRoomOverlapViolation(exception)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Phòng mới vừa bị booking khác chiếm trong thời gian lưu trú");
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public List<CheckInOptionResponse> getCheckInOptions() {
        return bookingRepository.findCheckInOptions(BookingStatus.DA_XAC_NHAN, RoomStatus.TRONG_SACH)
                .stream()
                .map(CheckInOptionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<com.ttcs.homestay.dto.booking.BookingRoomChangeHistoryResponse> getRoomChangeHistory(Long bookingId) {
        if (!bookingRepository.existsById(bookingId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy booking");
        }
        return roomChangeHistoryRepository.findAllByBookingIdOrderByChangedAtDescIdDesc(bookingId)
                .stream().map(com.ttcs.homestay.dto.booking.BookingRoomChangeHistoryResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<com.ttcs.homestay.dto.RoomResponse> getAvailableRoomsForBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy booking"));
        if (booking.getStatus() == BookingStatus.DA_NHAN_PHONG) {
            LocalDate start = remainingStayStart(booking, LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
            return roomAvailabilityService.listAvailableRooms(booking.getRoomType(), start,
                            booking.getCheckOutDate(), booking.getId()).stream()
                    .filter(room -> room.getStatus() == RoomStatus.TRONG_SACH)
                    .filter(room -> booking.getRoom() == null || !room.getId().equals(booking.getRoom().getId()))
                    .filter(room -> roomAvailabilityService.satisfiesRoomOverlapConstraint(room, booking))
                    .map(com.ttcs.homestay.dto.RoomResponse::from).toList();
        }
        if (booking.getStatus() != BookingStatus.DA_XAC_NHAN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ booking đã xác nhận mới được chọn phòng");
        }
        return roomAvailabilityService.listAvailableRooms(
                        booking.getRoomType(), booking.getCheckInDate(), booking.getCheckOutDate(), booking.getId())
                .stream().map(com.ttcs.homestay.dto.RoomResponse::from).toList();
    }

    private LocalDate remainingStayStart(Booking booking, LocalDate today) {
        if (booking.getCheckInDate() == null || booking.getCheckOutDate() == null
                || booking.getCheckInDate().isAfter(today) || !booking.getCheckOutDate().isAfter(today)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Booking không có thời gian lưu trú còn lại hợp lệ để đổi phòng");
        }
        return today;
    }

    private Room lockRoomForChange(Long roomId) {
        return roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Phòng không còn tồn tại. Vui lòng tải lại danh sách phòng"));
    }

    @Transactional
    public BookingResponse assignRoom(Long bookingId, Long roomId) {
        if (roomRepository == null) {
            throw new IllegalStateException("RoomRepository is required for room assignment");
        }
        Booking bookingSnapshot = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy booking"));
        if (bookingSnapshot.getRoomType() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking không có loại phòng hợp lệ");
        }
        // Cùng thứ tự khóa với tạo/sửa booking để tuần tự hóa chốt phòng và tự giữ phòng tạm.
        roomTypeRepository.findByIdForUpdate(bookingSnapshot.getRoomType().getId());
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy phòng"));
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy booking"));
        if (booking.getStatus() != BookingStatus.DA_XAC_NHAN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chỉ booking đã xác nhận mới được chọn phòng");
        }
        if (booking.getRoomConfirmedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Booking đã chốt phòng, không thể đổi trong lát này");
        }
        List<Booking> conflicts = bookingRepository.findRoomConflicts(
                roomId, booking.getId(), booking.getCheckInDate(), booking.getCheckOutDate(),
                RoomAvailabilityService.OCCUPYING_STATUSES).stream()
                .filter(conflict -> conflict.getStatus() != BookingStatus.CHO_XAC_NHAN
                        || conflict.getHoldExpiresAt() == null
                        || conflict.getHoldExpiresAt().isAfter(java.time.OffsetDateTime.now()))
                .toList();
        if (!conflicts.isEmpty()) {
            String codes = conflicts.stream().map(conflict -> conflict.getBookingCode()).distinct().sorted()
                    .collect(java.util.stream.Collectors.joining(", "));
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Phòng đã bị booking chiếm trong kỳ lưu trú: " + codes);
        }
        if (!roomAvailabilityService.isRoomAvailable(room, booking.getRoomType(), booking.getCheckInDate(),
                booking.getCheckOutDate(), booking.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Phòng không phù hợp, đang bảo trì hoặc đã được đặt trong khoảng lưu trú");
        }
        booking.setRoom(room);
        ActorInfo actor = resolveCurrentActor();
        if (actor.id() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Không xác định được tài khoản chốt phòng");
        }
        booking.setRoomConfirmedAt(OffsetDateTime.now());
        booking.setRoomConfirmedByUser(entityManager.getReference(com.ttcs.homestay.entity.User.class, actor.id()));
        try {
            Booking saved = bookingRepository.save(booking);
            bookingRepository.flush();
            return BookingResponse.from(saved);
        } catch (DataIntegrityViolationException exception) {
            if (RoomAvailabilityService.isRoomOverlapViolation(exception)) {
                String codes = bookingRepository.findRoomConflicts(
                                roomId, booking.getId(), booking.getCheckInDate(), booking.getCheckOutDate(),
                                RoomAvailabilityService.OCCUPYING_STATUSES).stream()
                        .filter(conflict -> conflict.getStatus() != BookingStatus.CHO_XAC_NHAN
                                || conflict.getHoldExpiresAt() == null
                                || conflict.getHoldExpiresAt().isAfter(java.time.OffsetDateTime.now()))
                        .map(conflict -> conflict.getBookingCode()).distinct().sorted()
                        .collect(java.util.stream.Collectors.joining(", "));
                String detail = codes.isBlank() ? "booking khác (yêu cầu đồng thời)" : codes;
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Phòng vừa bị chiếm trong kỳ lưu trú bởi booking: " + detail);
            }
            throw exception;
        }
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
        /** S3-02 Lát 3: chỉ huỷ được booking chưa nhận phòng và còn hiệu lực. */
    private static final Set<BookingStatus> CANCELLABLE_STATUSES =
            Set.of(BookingStatus.CHO_XAC_NHAN, BookingStatus.DA_XAC_NHAN);

    /**
     * S3-02 Lát 3: lễ tân huỷ booking. Booking đã huỷ thôi chiếm phòng (ràng buộc V36 và phép tính phòng trống
     * chỉ tính booking đang chiếm phòng) nên phòng trở lại kết quả tra phòng trống ngay. Hoàn cọc thuộc S3-05.
     */
    @Transactional
    public BookingResponse cancelBooking(Long id, BookingCancelRequest request) {
        String reason = request == null || request.reason() == null ? "" : request.reason().trim();
        if (reason.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do huỷ booking");
        }

        Booking booking = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy booking"));
        if (booking.getStatus() == BookingStatus.DA_NHAN_PHONG) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Booking đã nhận phòng không huỷ được, vui lòng làm thủ tục trả phòng sớm");
        }
        if (!CANCELLABLE_STATUSES.contains(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ huỷ được booking đang chờ xác nhận hoặc đã xác nhận");
        }

        ActorInfo actor = resolveCurrentActor();
        booking.setStatus(BookingStatus.DA_HUY);
        booking.setCancelReason(reason);
        booking.setCancelledBy(actor.email() != null ? actor.email() : actor.name());
        booking.setCancelledAt(OffsetDateTime.now());
        Booking saved = bookingRepository.save(booking);

        String ipAddress = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            ipAddress = attrs.getRequest().getRemoteAddr();
        }
        auditLogService.recordSensitiveAction(
                actor.id(), actor.email(), null, "Booking " + saved.getBookingCode(), "BOOKING_CANCELLED", ipAddress);
        return BookingResponse.from(saved);
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
            // S3-02 Lát 2: khoá loại phòng để các yêu cầu đặt cùng loại phòng xếp hàng lần lượt;
        // yêu cầu đến sau thấy phòng đã bị giữ và được gán phòng khác hoặc nhận 409.
        roomTypeRepository.findByIdForUpdate(roomType.getId());
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
        // S3-03: giữ một suất theo loại phòng; lễ tân gán phòng cụ thể sau khi xác nhận.

        Booking savedBooking = saveHoldingRoom(booking, roomType);

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
        
        // S3-02 Lát 2: khoá loại phòng trước khi khoá booking (cùng thứ tự với luồng khách đặt) để các yêu cầu cùng loại phòng xếp hàng.
        roomTypeRepository.findByIdForUpdate(request.roomTypeId());

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
        // Phòng chưa chốt là phòng giữ tạm: cập nhật ngày/loại phòng thì chọn lại theo yêu cầu mới.
        if (booking.getRoomConfirmedAt() == null) {
            booking.setRoom(null);
        }
        // Phòng đã chốt phải tiếp tục phù hợp với loại phòng và khoảng lưu trú.
        if (booking.getRoomConfirmedAt() != null && booking.getRoom() != null
                && !roomAvailabilityService.isRoomAvailable(
                        booking.getRoom(), roomType, request.checkInDate(), request.checkOutDate(), booking.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Phòng " + booking.getRoom().getRoomNumber()
                            + " không còn phù hợp với loại phòng hoặc khoảng lưu trú mới");
        }

        Booking savedBooking = saveHoldingRoom(booking, roomType);

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

    /** S3-02: lưu booking đang giữ phòng; cơ sở dữ liệu từ chối do trùng phòng cùng đêm thì báo hết phòng (409). */
    private Booking saveHoldingRoom(Booking booking, RoomType roomType) {
        try {
            if (booking.getRoom() == null) {
                booking.setRoom(roomAvailabilityService.assignRoom(
                        roomType, booking.getCheckInDate(), booking.getCheckOutDate(), booking.getId(), null));
            }
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
