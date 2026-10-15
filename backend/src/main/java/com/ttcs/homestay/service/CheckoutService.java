package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.CheckoutPaymentRequest;
import com.ttcs.homestay.dto.CheckoutPreviewResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingNightlyPriceSnapshot;
import com.ttcs.homestay.entity.BookingSettlement;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.Room;
import com.ttcs.homestay.entity.RoomStatus;
import com.ttcs.homestay.entity.RoomStatusHistory;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.BookingSettlementRepository;
import com.ttcs.homestay.repository.RoomRepository;
import com.ttcs.homestay.repository.RoomStatusHistoryRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CheckoutService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final RoomRepository roomRepository;
    private final BookingRepository bookingRepository;
    private final BookingSettlementRepository settlementRepository;
    private final RoomStatusHistoryRepository historyRepository;
    private final BookingDepositService depositService;

    public CheckoutService(RoomRepository roomRepository, BookingRepository bookingRepository,
            BookingSettlementRepository settlementRepository, RoomStatusHistoryRepository historyRepository,
            BookingDepositService depositService) {
        this.roomRepository = roomRepository;
        this.bookingRepository = bookingRepository;
        this.settlementRepository = settlementRepository;
        this.historyRepository = historyRepository;
        this.depositService = depositService;
    }

    @Transactional(readOnly = true)
    public CheckoutPreviewResponse preview(Long roomId) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> notFound("Không tìm thấy phòng"));
        requireOccupied(room);
        Booking booking = findSingleInStayBooking(roomId);
        if (booking.getRoom() == null || !roomId.equals(booking.getRoom().getId())) {
            throw conflict("Booking đang lưu trú không thuộc phòng này");
        }
        return buildPreview(room, booking);
    }

    @Transactional
    public Room checkout(Long roomId, CheckoutPaymentRequest request, String operatorName) {
        // Consistent lock order: room first, then in-stay booking(s), sorted by id by repository query.
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> notFound("Không tìm thấy phòng"));
        requireOccupied(room);
        List<Booking> bookings = bookingRepository.findByRoomAndStatusForUpdate(roomId, BookingStatus.DA_NHAN_PHONG);
        if (bookings.isEmpty()) throw conflict("Không tìm thấy booking đang lưu trú cho phòng này");
        if (bookings.size() != 1) throw conflict("Có nhiều booking đang lưu trú cùng phòng; cần xử lý dữ liệu trước");
        Booking booking = bookings.get(0);
        if (booking.getRoom() == null || !roomId.equals(booking.getRoom().getId())) {
            throw conflict("Booking đang lưu trú không thuộc phòng này");
        }
        if (settlementRepository.findByBookingId(booking.getId()).isPresent()) {
            throw conflict("Booking này đã có quyết toán");
        }

        CheckoutPreviewResponse current = buildPreview(room, booking);
        if (request == null || !current.previewToken().equals(request.previewToken())) {
            throw conflict("Dữ liệu quyết toán đã thay đổi. Hãy tải lại preview và xác nhận lại");
        }
        validateConfirmation(request, current);

        OffsetDateTime now = OffsetDateTime.now(BUSINESS_ZONE);
        BookingSettlement settlement = new BookingSettlement();
        settlement.setBooking(booking);
        settlement.setRoomId(roomId);
        settlement.setRoomTotal(current.roomTotal());
        settlement.setDepositTotal(current.depositTotal());
        settlement.setSettlementType(BookingSettlement.SettlementType.valueOf(current.settlementType()));
        settlement.setAmount(current.amount());
        boolean cashTransaction = current.amount().signum() > 0;
        settlement.setPaymentMethod(cashTransaction
                ? BookingSettlement.SettlementPaymentMethod.valueOf(request.paymentMethod()) : null);
        settlement.setPaymentReference(cashTransaction ? normalize(request.paymentReference()) : null);
        settlement.setTransactionStatus("ZERO".equals(current.settlementType()) ? "NOT_APPLICABLE" : "CONFIRMED");
        settlement.setActedBy(operatorName == null || operatorName.isBlank() ? "RECEPTIONIST" : operatorName);
        settlement.setSettledAt(now);
        settlement.setCheckedOutAt(now);
        settlementRepository.save(settlement);

        booking.setStatus(BookingStatus.DA_TRA_PHONG);
        bookingRepository.save(booking);
        room.setStatus(RoomStatus.TRONG_BAN);
        room.setMaintenanceReason(null);
        room.setMaintenanceStartDate(null);
        room.setMaintenanceEndDate(null);
        Room savedRoom = roomRepository.save(room);

        RoomStatusHistory history = new RoomStatusHistory();
        history.setRoom(savedRoom);
        history.setPreviousStatus(RoomStatus.DANG_O);
        history.setNewStatus(RoomStatus.TRONG_BAN);
        history.setChangedBy(settlement.getActedBy());
        history.setChangedAt(now);
        historyRepository.save(history);
        return savedRoom;
    }

    private CheckoutPreviewResponse buildPreview(Room room, Booking booking) {
        BigDecimal roomTotal = BigDecimal.valueOf(booking.getTotalAmount());
        // Legacy rows with a missing/null DB value are represented as zero; never infer
        // an amount from today's room type fee or add this informational amount to roomTotal.
        BigDecimal surchargeAmount = BigDecimal.valueOf(Math.max(0L, booking.getSurchargeAmount()));
        long surchargeNights = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        List<BookingNightlyPriceSnapshot> stored = booking.getNightlyPriceSnapshots();
        SurchargeBreakdown surcharge = buildSurchargeBreakdown(booking, stored, surchargeNights);
        BigDecimal deposit = depositService.currentDepositTotalExact(booking);
        BigDecimal difference = roomTotal.subtract(deposit);
        String type = difference.signum() > 0 ? "COLLECT" : difference.signum() < 0 ? "REFUND" : "ZERO";
        long storedTotal = 0;
        for (BookingNightlyPriceSnapshot line : stored) {
            storedTotal = Math.addExact(storedTotal, Math.addExact(line.getRoomPrice(), line.getSurchargeAmount()));
        }
        long expectedNights = java.time.temporal.ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        List<BookingNightlyPriceSnapshot> orderedStored = stored.stream()
                .sorted(java.util.Comparator.comparing(BookingNightlyPriceSnapshot::getNightDate)).toList();
        boolean datesMatch = orderedStored.size() == expectedNights;
        for (int index = 0; datesMatch && index < orderedStored.size(); index++) {
            datesMatch = booking.getCheckInDate().plusDays(index).equals(orderedStored.get(index).getNightDate());
        }
        boolean reliable = !stored.isEmpty() && datesMatch && storedTotal == booking.getTotalAmount();
        List<CheckoutPreviewResponse.NightlyLine> lines = reliable ? stored.stream()
                .sorted(java.util.Comparator.comparing(BookingNightlyPriceSnapshot::getNightDate))
                .map(line -> new CheckoutPreviewResponse.NightlyLine(line.getNightDate(), line.getPriceLabel(),
                        line.getRoomPrice(), line.getSurchargeAmount(),
                        Math.addExact(line.getRoomPrice(), line.getSurchargeAmount())))
                .toList() : List.of();
        String warning = reliable ? null
                : "Booking cũ chưa có chi tiết giá từng đêm đáng tin cậy. Quyết toán theo tổng tiền đã chốt.";
        String fingerprint = booking.getId() + "|" + room.getId() + "|" + booking.getStatus() + "|"
                + booking.getTotalAmount() + "|" + booking.getExtraGuestCount() + "|"
                + booking.getExtraPersonFeeSnapshot() + "|" + surchargeAmount.toPlainString() + "|"
                + deposit.toPlainString() + "|" + booking.getCheckInDate() + "|"
                + booking.getCheckOutDate() + "|" + (reliable ? stored.stream()
                        .sorted(java.util.Comparator.comparing(BookingNightlyPriceSnapshot::getNightDate))
                        .map(x -> x.getNightDate() + ":" + x.getRoomPrice() + ":" + x.getSurchargeAmount())
                        .toList() : "NO_RELIABLE_BREAKDOWN");
        return new CheckoutPreviewResponse(booking.getId(), booking.getBookingCode(), room.getId(),
                room.getRoomNumber(), booking.getGuestName(), booking.getCheckInDate(), booking.getCheckOutDate(),
                lines, reliable, warning, roomTotal, surchargeAmount, surcharge.complete(),
                surcharge.complete() ? booking.getExtraGuestCount() : null,
                surcharge.complete() ? booking.getExtraPersonFeeSnapshot() : null,
                surcharge.complete() ? surchargeNights : null,
                surcharge.roomTotalExcludingSurcharge(), surcharge.warning(),
                deposit, type, difference.abs(), token(fingerprint));
    }

    private static SurchargeBreakdown buildSurchargeBreakdown(Booking booking,
            List<BookingNightlyPriceSnapshot> snapshots, long nights) {
        long count = booking.getExtraGuestCount();
        long fee = booking.getExtraPersonFeeSnapshot();
        long surcharge = booking.getSurchargeAmount();
        if (count < 0 || fee < 0 || surcharge < 0 || nights <= 0) {
            return SurchargeBreakdown.incomplete("Không đủ dữ liệu để tách phụ thu; chỉ hiển thị tổng phụ thu đã lưu.");
        }
        long calculated;
        try {
            calculated = Math.multiplyExact(Math.multiplyExact(count, fee), nights);
        } catch (ArithmeticException exception) {
            return SurchargeBreakdown.incomplete("Dữ liệu phụ thu không khớp; chỉ hiển thị tổng phụ thu đã lưu.");
        }
        if (calculated != surcharge || (surcharge > 0 && (count == 0 || fee == 0))) {
            return SurchargeBreakdown.incomplete("Dữ liệu phụ thu không khớp; chỉ hiển thị tổng phụ thu đã lưu.");
        }

        List<BookingNightlyPriceSnapshot> ordered = snapshots.stream()
                .sorted(java.util.Comparator.comparing(BookingNightlyPriceSnapshot::getNightDate)).toList();
        long snapshotNightSurcharge = 0;
        if (!ordered.isEmpty()) {
            if (ordered.size() != nights) {
                return SurchargeBreakdown.incomplete("Chi tiết phụ thu theo đêm không khớp booking; chỉ hiển thị tổng phụ thu đã lưu.");
            }
            for (int index = 0; index < ordered.size(); index++) {
                BookingNightlyPriceSnapshot line = ordered.get(index);
                if (!booking.getCheckInDate().plusDays(index).equals(line.getNightDate())) {
                    return SurchargeBreakdown.incomplete("Chi tiết phụ thu theo đêm không khớp booking; chỉ hiển thị tổng phụ thu đã lưu.");
                }
                try {
                    snapshotNightSurcharge = Math.addExact(snapshotNightSurcharge, line.getSurchargeAmount());
                } catch (ArithmeticException exception) {
                    return SurchargeBreakdown.incomplete("Chi tiết phụ thu theo đêm không khớp booking; chỉ hiển thị tổng phụ thu đã lưu.");
                }
            }
            if (snapshotNightSurcharge != surcharge) {
                return SurchargeBreakdown.incomplete("Chi tiết phụ thu theo đêm không khớp booking; chỉ hiển thị tổng phụ thu đã lưu.");
            }
        } else if (surcharge != 0) {
            return SurchargeBreakdown.incomplete("Booking không có snapshot theo đêm; chỉ hiển thị tổng phụ thu đã lưu.");
        } else if (nights > 0) {
            // With no nightly source rows, a zero surcharge is only attributable to
            // the booking summary when its recorded quantity or saved fee is zero.
            if (count != 0 && fee != 0) {
                return SurchargeBreakdown.incomplete("Booking không có snapshot theo đêm để xác thực chi tiết phụ thu.");
            }
        }

        return new SurchargeBreakdown(true,
                BigDecimal.valueOf(booking.getTotalAmount()).subtract(BigDecimal.valueOf(surcharge)), null);
    }

    private record SurchargeBreakdown(boolean complete, BigDecimal roomTotalExcludingSurcharge, String warning) {
        private static SurchargeBreakdown incomplete(String warning) {
            return new SurchargeBreakdown(false, null, warning);
        }
    }

    private Booking findSingleInStayBooking(Long roomId) {
        List<Booking> bookings = bookingRepository.findByRoomAndStatus(roomId, BookingStatus.DA_NHAN_PHONG);
        if (bookings.isEmpty()) throw conflict("Không tìm thấy booking đang lưu trú cho phòng này");
        if (bookings.size() != 1) throw conflict("Có nhiều booking đang lưu trú cùng phòng; cần xử lý dữ liệu trước");
        return bookings.get(0);
    }

    private static void validateConfirmation(CheckoutPaymentRequest request, CheckoutPreviewResponse preview) {
        if ("ZERO".equals(preview.settlementType())) return;
        if ("COLLECT".equals(preview.settlementType()) && !Boolean.TRUE.equals(request.amountReceivedConfirmed())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cần xác nhận đã thu đủ tiền");
        }
        if ("REFUND".equals(preview.settlementType()) && !Boolean.TRUE.equals(request.refundCompletedConfirmed())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cần xác nhận đã hoàn tiền thực tế");
        }
        if (request.paymentMethod() == null || (!"CASH".equals(request.paymentMethod())
                && !"BANK_TRANSFER".equals(request.paymentMethod()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Phương thức giao dịch không hợp lệ");
        }
        if (request.paymentReference() != null && request.paymentReference().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mã giao dịch không được vượt quá 255 ký tự");
        }
        if ("BANK_TRANSFER".equals(request.paymentMethod())
                && (request.paymentReference() == null || request.paymentReference().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chuyển khoản bắt buộc có mã giao dịch");
        }
    }

    private static void requireOccupied(Room room) {
        if (room.getStatus() != RoomStatus.DANG_O) throw conflict("Phòng không ở trạng thái Đang ở");
    }

    private static String token(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
