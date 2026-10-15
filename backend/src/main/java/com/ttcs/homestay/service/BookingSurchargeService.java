package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.booking.BookingSurchargeListResponse;
import com.ttcs.homestay.dto.booking.BookingSurchargeRequest;
import com.ttcs.homestay.dto.booking.BookingSurchargeResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.BookingSurcharge;
import com.ttcs.homestay.entity.SurchargeType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.BookingGuestRepository;
import com.ttcs.homestay.repository.BookingSurchargeRepository;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * S4-02 Lát 1: thêm dòng phụ thu thêm người / thêm giường vào booking.
 * Thành tiền = số lượng × đơn giá × số đêm; đơn giá lấy tự động theo loại.
 */
@Service
public class BookingSurchargeService {

    /** Đề xuất chờ PO chốt: chỉ booking đã xác nhận hoặc đang ở. */
    static final Set<BookingStatus> ALLOWED_STATUSES =
            EnumSet.of(BookingStatus.DA_XAC_NHAN, BookingStatus.DA_NHAN_PHONG);
    static final int MAX_QUANTITY = 10;
    static final int MAX_NOTE_LENGTH = 500;
    static final long MAX_UNIT_PRICE = 100_000_000L;
    private final BookingRepository bookingRepository;
    private final BookingSurchargeRepository surchargeRepository;
    private final OperatingSettingsRepository settingsRepository;
    private final BookingGuestRepository guestRepository;

    public BookingSurchargeService(
            BookingRepository bookingRepository,
            BookingSurchargeRepository surchargeRepository,
            OperatingSettingsRepository settingsRepository,
            BookingGuestRepository guestRepository) {
        this.bookingRepository = bookingRepository;
        this.surchargeRepository = surchargeRepository;
        this.settingsRepository = settingsRepository;
        this.guestRepository = guestRepository;
    }

    @Transactional(readOnly = true)
    public BookingSurchargeListResponse getSurcharges(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(BookingSurchargeService::bookingNotFound);
        return toListResponse(booking);
    }

    @Transactional
    public BookingSurchargeListResponse addSurcharge(Long bookingId, BookingSurchargeRequest request) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(BookingSurchargeService::bookingNotFound);

        if (!ALLOWED_STATUSES.contains(booking.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ thêm phụ thu cho booking đã xác nhận hoặc đang ở");
        }
        validate(request, maxNights(booking));
        // S4-02 Lát 3: dòng "thêm người" không được làm tổng số khách vượt sức chứa tối đa của loại phòng.
        if (request.type() == SurchargeType.THEM_NGUOI) {
            ensureCapacity(booking, request.quantity());
        }

        long defaultPrice = defaultUnitPrice(booking, request.type());
        // S4-02 Lát 4: lễ tân được sửa tay đơn giá nhưng bắt buộc nhập lý do.
        long unitPrice = request.unitPrice() == null ? defaultPrice : request.unitPrice();
        String priceReason = null;
        if (unitPrice != defaultPrice) {
            if (request.priceReason() == null || request.priceReason().isBlank()) {
                throw badRequest("Đơn giá khác giá mặc định, vui lòng nhập lý do sửa giá");
            }
            priceReason = request.priceReason().trim();
        }
        String actor = currentActorName();

        BookingSurcharge surcharge = new BookingSurcharge();
        surcharge.setBooking(booking);
        surcharge.setSurchargeType(request.type());
        surcharge.setQuantity(request.quantity());
        surcharge.setUnitPrice(unitPrice);
        surcharge.setDefaultUnitPrice(defaultPrice);
        surcharge.setPriceOverrideReason(priceReason);
        surcharge.setPriceOverriddenBy(priceReason == null ? null : actor);
        surcharge.setNights(request.nights());
        surcharge.setAmount(amountOf(request.quantity(), unitPrice, request.nights()));
        surcharge.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        surcharge.setCreatedBy(actor);
        surcharge.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        surchargeRepository.saveAndFlush(surcharge);

        return toListResponse(booking);
    }

    /** Thành tiền = số lượng × đơn giá × số đêm. */
    static long amountOf(int quantity, long unitPrice, int nights) {
        return Math.multiplyExact(Math.multiplyExact((long) quantity, unitPrice), (long) nights);
    }

    private long defaultUnitPrice(Booking booking, SurchargeType type) {
        if (type == SurchargeType.THEM_NGUOI) {
            // Cùng nguồn với báo giá lúc đặt phòng (S2-06): phụ thu thêm khách của loại phòng.
            if (booking.getRoomType() == null || booking.getRoomType().getExtraGuestFee() == null) {
                return 0L;
            }
            return booking.getRoomType().getExtraGuestFee();
        }
        // Thêm giường: tham số vận hành đang có hiệu lực (phiên bản mới nhất).
        return settingsRepository.findFirstByOrderByCreatedAtDescIdDesc()
                .map(settings -> settings.getExtraBedFee())
                .orElse(0L);
    }

    private BookingSurchargeListResponse toListResponse(Booking booking) {
        List<BookingSurchargeResponse> items = surchargeRepository
                .findByBookingIdOrderByCreatedAtAscIdAsc(booking.getId())
                .stream()
                .map(BookingSurchargeResponse::from)
                .toList();
        long totalAmount = items.stream().mapToLong(item -> item.amount()).sum();

        int currentGuests = currentGuestCount(booking);
        Integer maxCapacity = maxCapacity(booking);
        Integer remainingCapacity = maxCapacity == null ? null : Math.max(0, maxCapacity - currentGuests);
        AutomaticSurchargeSnapshot automatic = automaticSurchargeSnapshot(booking);

        return new BookingSurchargeListResponse(
                booking.getId(),
                ALLOWED_STATUSES.contains(booking.getStatus()),
                maxNights(booking),
                defaultUnitPrice(booking, SurchargeType.THEM_NGUOI),
                defaultUnitPrice(booking, SurchargeType.THEM_GIUONG),
                items,
                totalAmount,
                currentGuests,
                maxCapacity,
                remainingCapacity,
                booking.getExtraGuestCount(),
                booking.getExtraPersonFeeSnapshot(),
                booking.getSurchargeAmount(),
                automatic.complete(),
                automatic.warning());
    }

    private static AutomaticSurchargeSnapshot automaticSurchargeSnapshot(Booking booking) {
        long nights = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        List<com.ttcs.homestay.entity.BookingNightlyPriceSnapshot> snapshots = booking.getNightlyPriceSnapshots();
        if (nights <= 0 || snapshots.size() != nights) {
            return new AutomaticSurchargeSnapshot(false, "Thiếu snapshot giá theo đêm để xác thực phụ thu đã chốt.");
        }
        long calculated;
        long nightlyTotal = 0;
        try {
            calculated = Math.multiplyExact(Math.multiplyExact((long) booking.getExtraGuestCount(),
                    booking.getExtraPersonFeeSnapshot()), nights);
            List<com.ttcs.homestay.entity.BookingNightlyPriceSnapshot> ordered = snapshots.stream()
                    .sorted(java.util.Comparator.comparing(
                            com.ttcs.homestay.entity.BookingNightlyPriceSnapshot::getNightDate)).toList();
            for (int i = 0; i < ordered.size(); i++) {
                if (!booking.getCheckInDate().plusDays(i).equals(ordered.get(i).getNightDate())) {
                    return new AutomaticSurchargeSnapshot(false, "Snapshot giá theo đêm không khớp thời gian booking.");
                }
                nightlyTotal = Math.addExact(nightlyTotal, ordered.get(i).getSurchargeAmount());
            }
        } catch (ArithmeticException ex) {
            return new AutomaticSurchargeSnapshot(false, "Snapshot phụ thu tự động không hợp lệ.");
        }
        if (booking.getExtraGuestCount() < 0 || booking.getExtraPersonFeeSnapshot() < 0
                || booking.getSurchargeAmount() < 0 || calculated != booking.getSurchargeAmount()
                || nightlyTotal != booking.getSurchargeAmount()) {
            return new AutomaticSurchargeSnapshot(false, "Snapshot phụ thu tự động không nhất quán.");
        }
        return new AutomaticSurchargeSnapshot(true, null);
    }

    private record AutomaticSurchargeSnapshot(boolean complete, String warning) {}

    /** S4-02 Lát 3: số khách hiện tại = khách gốc của booking + số người của các dòng "thêm người" đã lưu. */
    private int currentGuestCount(Booking booking) {
        return baseGuestCount(booking) + addedGuestCount(booking.getId());
    }

    /** Khách gốc: số khách đã đăng ký khi nhận phòng; chưa nhận phòng thì lấy số khách lúc đặt; thiếu dữ liệu thì tính 1. */
    private int baseGuestCount(Booking booking) {
        int registered = guestRepository.findAllByBookingIdOrderByGuestOrderAsc(booking.getId()).size();
        if (registered > 0) {
            return registered;
        }
        Integer booked = booking.getGuestCount();
        return booked != null && booked > 0 ? booked : 1;
    }

    /** Đề xuất chờ PO chốt: cộng dồn mọi dòng "thêm người"; dòng "thêm giường" không tính khách. */
    private int addedGuestCount(Long bookingId) {
        return surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(bookingId).stream()
                .filter(line -> line.getSurchargeType() == SurchargeType.THEM_NGUOI)
                .mapToInt(line -> line.getQuantity())
                .sum();
    }

    private static Integer maxCapacity(Booking booking) {
        return booking.getRoomType() == null ? null : booking.getRoomType().getMaxCapacity();
    }

    private void ensureCapacity(Booking booking, int addedQuantity) {
        Integer max = maxCapacity(booking);
        if (max == null) {
            return;
        }
        int current = currentGuestCount(booking);
        if (current + addedQuantity > max) {
            int remaining = Math.max(0, max - current);
            String hint = remaining == 0
                    ? "không thể thêm người."
                    : "chỉ thêm được tối đa " + remaining + " người.";
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Vượt sức chứa tối đa " + max + " khách của loại phòng. Booking đang có "
                            + current + " khách, " + hint);
        }
    }

    private static int maxNights(Booking booking) {
        long nights = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        return (int) Math.max(1, nights);
    }

    private static void validate(BookingSurchargeRequest request, int maxNights) {
        if (request == null || request.type() == null) {
            throw badRequest("Vui lòng chọn loại phụ thu");
        }
        if (request.quantity() == null || request.quantity() < 1) {
            throw badRequest("Số lượng phải từ 1 trở lên");
        }
        if (request.quantity() > MAX_QUANTITY) {
            throw badRequest("Số lượng tối đa là " + MAX_QUANTITY);
        }
        if (request.nights() == null || request.nights() < 1) {
            throw badRequest("Số đêm áp dụng phải từ 1 trở lên");
        }
        if (request.nights() > maxNights) {
            throw badRequest("Số đêm áp dụng không được vượt quá " + maxNights + " đêm của booking");
        }
        if (request.unitPrice() != null && (request.unitPrice() < 0 || request.unitPrice() > MAX_UNIT_PRICE)) {
            throw badRequest("Đơn giá phải từ 0 đến 100.000.000 đ");
        }
        if (request.priceReason() != null && request.priceReason().length() > MAX_NOTE_LENGTH) {
            throw badRequest("Lý do sửa giá tối đa 500 ký tự");
        }
        if (request.note() != null && request.note().length() > MAX_NOTE_LENGTH) {
            throw badRequest("Ghi chú tối đa 500 ký tự");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static ResponseStatusException bookingNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy booking");
    }

    private String currentActorName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            String actorName = token.getToken().getClaimAsString("fullName");
            if (actorName == null || actorName.isBlank()) {
                actorName = token.getToken().getClaimAsString("email");
            }
            if (actorName != null && !actorName.isBlank()) {
                return actorName;
            }
        }
        if (authentication != null && authentication.getName() != null && !authentication.getName().isBlank()) {
            return authentication.getName();
        }
        return "Không rõ";
    }
}
