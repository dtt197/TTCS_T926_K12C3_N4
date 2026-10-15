package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.InvoiceData;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingSettlement;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.BookingSurcharge;
import com.ttcs.homestay.entity.BookingNightlyPriceSnapshot;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.entity.SurchargeType;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.BookingSettlementRepository;
import com.ttcs.homestay.repository.BookingSurchargeRepository;
import com.ttcs.homestay.repository.OperatingSettingsRepository;
import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Assembles invoice data from the persisted checkout settlement. */
@Service
public class InvoiceDataService {
    private final BookingRepository bookingRepository;
    private final BookingSettlementRepository settlementRepository;
    private final BookingSurchargeRepository surchargeRepository;
    private final OperatingSettingsRepository settingsRepository;

    public InvoiceDataService(BookingRepository bookingRepository,
            BookingSettlementRepository settlementRepository,
            BookingSurchargeRepository surchargeRepository,
            OperatingSettingsRepository settingsRepository) {
        this.bookingRepository = bookingRepository;
        this.settlementRepository = settlementRepository;
        this.surchargeRepository = surchargeRepository;
        this.settingsRepository = settingsRepository;
    }

    @Transactional(readOnly = true)
    public InvoiceData getInvoiceData(long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Không tìm thấy booking để lập hóa đơn."));
        if (booking.getStatus() != BookingStatus.DA_TRA_PHONG) {
            throw error(HttpStatus.CONFLICT, "Chỉ lập hóa đơn cho booking đã trả phòng (DA_TRA_PHONG).");
        }
        BookingSettlement settlement = settlementRepository.findByBookingId(bookingId)
                .orElseThrow(() -> error(HttpStatus.CONFLICT,
                        "Booking đã trả phòng nhưng chưa có dữ liệu quyết toán (settlement)."));
        Long settingsId = settlement.getOperatingSettingsId();
        OperatingSettings settings = settingsId == null ? null : settingsRepository.findById(settingsId).orElse(null);
        boolean contactInfoSupplemented = false;
        if (settings != null && (isBlank(settings.getAddress()) || isBlank(settings.getPhone()))) {
            OperatingSettings current = settingsRepository.findFirstByOrderByCreatedAtDescIdDesc().orElse(null);
            if (current != null) {
                contactInfoSupplemented = true;
                settings = withCurrentContactFallback(settings, current);
            }
        }
        if (settings == null) {
            throw error(HttpStatus.CONFLICT, "Không tìm thấy thông tin homestay đã dùng khi quyết toán.");
        }
        requireText(settings.getHomestayName(), "Thiếu tên homestay trong cấu hình.");
        requireText(settings.getAddress(), "Thiếu địa chỉ homestay trong cấu hình.");
        requireText(settings.getPhone(), "Thiếu số điện thoại homestay trong cấu hình.");
        if (booking.getRoom() == null || booking.getRoom().getId() == null) {
            throw error(HttpStatus.CONFLICT, "Booking không có phòng được gán; không thể lập hóa đơn.");
        }
        requireText(booking.getGuestName(), "Booking thiếu tên khách; không thể lập hóa đơn.");
        if (booking.getCheckInDate() == null || booking.getCheckOutDate() == null
                || !booking.getCheckOutDate().isAfter(booking.getCheckInDate())) {
            throw error(HttpStatus.CONFLICT, "Khoảng lưu trú của booking không hợp lệ.");
        }

        BigDecimal roomAmount = nonNegative(settlement.getRoomTotal(), "roomTotal");
        BigDecimal deposit = nonNegative(settlement.getDepositTotal(), "depositTotal");
        if (settlement.getFinalTotal() == null || settlement.getFinalTotal() < 0) {
            throw error(HttpStatus.CONFLICT, "Settlement thiếu finalTotal hợp lệ (tổng tiền trước cọc).");
        }
        BigDecimal totalBeforeDeposit = BigDecimal.valueOf(settlement.getFinalTotal());
        BigDecimal lateFee = BigDecimal.valueOf(settlement.getLateCheckoutFee());
        BigDecimal surchargeTotal = BigDecimal.valueOf(settlement.getAdditionalSurchargeTotal());
        if (lateFee.signum() < 0 || surchargeTotal.signum() < 0) {
            throw error(HttpStatus.CONFLICT, "Settlement chứa giá trị phụ thu âm, không thể lập hóa đơn.");
        }
        List<BookingSurcharge> surcharges = surchargeRepository.findByBookingIdOrderByCreatedAtAscIdAsc(bookingId);
        BigDecimal rowsTotal = surcharges.stream().map(line -> BigDecimal.valueOf(line.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (rowsTotal.compareTo(surchargeTotal) != 0) {
            throw error(HttpStatus.CONFLICT,
                    "Chi tiết phụ thu booking không khớp additionalSurchargeTotal trong settlement; "
                            + "không thể phân loại dòng tiền an toàn.");
        }
        AutomaticSurcharge automatic = automaticSurcharge(booking, roomAmount);
        BigDecimal roomBaseAmount = roomAmount.subtract(automatic.amount());
        if (roomBaseAmount.add(automatic.amount()).add(lateFee).add(surchargeTotal)
                .compareTo(totalBeforeDeposit) != 0) {
            throw error(HttpStatus.CONFLICT,
                    "Tổng tiền các dòng (tiền phòng + trả muộn + phụ thu) không khớp finalTotal trong settlement.");
        }

        BigDecimal difference = totalBeforeDeposit.subtract(deposit);
        BigDecimal amount = nonNegative(settlement.getAmount(), "amount");
        BookingSettlement.SettlementType expectedType = difference.signum() > 0
                ? BookingSettlement.SettlementType.COLLECT
                : difference.signum() < 0 ? BookingSettlement.SettlementType.REFUND
                        : BookingSettlement.SettlementType.ZERO;
        if (settlement.getSettlementType() != expectedType || amount.compareTo(difference.abs()) != 0) {
            throw error(HttpStatus.CONFLICT, "settlementType/amount không khớp finalTotal trừ depositTotal.");
        }

        List<InvoiceData.LineItem> lines = new ArrayList<>();
        lines.add(new InvoiceData.LineItem("ROOM", automatic.amount().signum() > 0
                ? "Tiền phòng cơ bản (chưa gồm phụ thu)" : "Tiền phòng", roomBaseAmount));
        addExtraPersonLine(lines, surcharges, automatic);
        addSurchargeLine(lines, surcharges, SurchargeType.THEM_GIUONG, "EXTRA_BED", "Thêm giường");
        lines.add(new InvoiceData.LineItem("LATE_CHECKOUT", "Trả muộn", lateFee));
        lines.add(new InvoiceData.LineItem("TOTAL_BEFORE_DEPOSIT", "Tổng tiền trước cọc", totalBeforeDeposit));
        lines.add(new InvoiceData.LineItem("DEPOSIT", "Trừ tiền cọc", deposit.negate()));
        String balanceLabel = expectedType == BookingSettlement.SettlementType.REFUND ? "Còn phải hoàn" : "Còn phải thu";
        lines.add(new InvoiceData.LineItem("BALANCE", balanceLabel,
                expectedType == BookingSettlement.SettlementType.REFUND ? amount.negate() : amount));

        return new InvoiceData(booking.getId(), booking.getBookingCode(), booking.getGuestName(),
                booking.getGuestPhone(), null, null,
                new InvoiceData.Homestay(settings.getHomestayName(), settings.getAddress(), settings.getPhone(), null, null),
                new InvoiceData.Room(booking.getRoom().getId(), booking.getRoom().getRoomNumber(),
                        booking.getRoomTypeNameSnapshot()),
                booking.getCheckInDate(), booking.getCheckOutDate(),
                ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate()),
                lines, totalBeforeDeposit, deposit, settlement.getSettlementType(), amount,
                difference, settlement.getCheckedOutAt(), contactInfoSupplemented);
    }

    private static AutomaticSurcharge automaticSurcharge(Booking booking, BigDecimal settledRoomAmount) {
        long nights = ChronoUnit.DAYS.between(booking.getCheckInDate(), booking.getCheckOutDate());
        List<BookingNightlyPriceSnapshot> snapshots = booking.getNightlyPriceSnapshots();
        if (nights <= 0 || snapshots == null || snapshots.size() != nights) {
            throw error(HttpStatus.CONFLICT,
                    "Thiếu snapshot theo đêm; không thể phân loại an toàn tiền phòng và phụ thu thêm người đã chốt.");
        }
        long calculated;
        long snapshotSurcharge = 0;
        long roomBase = 0;
        List<BookingNightlyPriceSnapshot> ordered = snapshots.stream()
                .sorted(java.util.Comparator.comparing(BookingNightlyPriceSnapshot::getNightDate)).toList();
        try {
            if (booking.getExtraGuestCount() < 0 || booking.getExtraPersonFeeSnapshot() < 0
                    || booking.getSurchargeAmount() < 0) throw new ArithmeticException("negative snapshot");
            calculated = Math.multiplyExact(Math.multiplyExact((long) booking.getExtraGuestCount(),
                    booking.getExtraPersonFeeSnapshot()), nights);
            for (int index = 0; index < ordered.size(); index++) {
                BookingNightlyPriceSnapshot line = ordered.get(index);
                if (!booking.getCheckInDate().plusDays(index).equals(line.getNightDate())
                        || line.getRoomPrice() < 0 || line.getSurchargeAmount() < 0) {
                    throw new ArithmeticException("invalid nightly snapshot");
                }
                snapshotSurcharge = Math.addExact(snapshotSurcharge, line.getSurchargeAmount());
                roomBase = Math.addExact(roomBase, line.getRoomPrice());
            }
        } catch (ArithmeticException exception) {
            throw error(HttpStatus.CONFLICT,
                    "Snapshot phụ thu thêm người đã chốt không hợp lệ; không thể phân loại an toàn hóa đơn.");
        }
        BigDecimal automaticAmount = BigDecimal.valueOf(booking.getSurchargeAmount());
        if (calculated != booking.getSurchargeAmount() || snapshotSurcharge != booking.getSurchargeAmount()
                || BigDecimal.valueOf(roomBase).add(automaticAmount).compareTo(settledRoomAmount) != 0
                || booking.getTotalAmount() != settledRoomAmount.longValueExact()) {
            throw error(HttpStatus.CONFLICT,
                    "Snapshot phụ thu thêm người đã chốt không khớp roomTotal; không thể phân loại an toàn hóa đơn.");
        }
        return new AutomaticSurcharge(automaticAmount, booking.getExtraGuestCount(),
                booking.getExtraPersonFeeSnapshot(), nights);
    }

    private static void addExtraPersonLine(List<InvoiceData.LineItem> lines, List<BookingSurcharge> surcharges,
            AutomaticSurcharge automatic) {
        List<BookingSurcharge> matching = surcharges.stream()
                .filter(line -> line.getSurchargeType() == SurchargeType.THEM_NGUOI).toList();
        BigDecimal manualAmount = matching.stream().map(line -> BigDecimal.valueOf(line.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String automaticDetails = automatic.amount().signum() == 0 ? "Phụ thu tự động đã chốt: 0"
                : "Phụ thu tự động đã chốt: " + automatic.extraGuestCount() + " khách × "
                        + automatic.unitPrice() + " đ/khách/đêm × " + automatic.nights() + " đêm = "
                        + automatic.amount().toPlainString() + " đ";
        String manualDetails = java.util.stream.IntStream.range(0, matching.size())
                .mapToObj(index -> surchargeDescription(index + 1, matching.get(index)))
                .collect(java.util.stream.Collectors.joining("; "));
        String description = "Thêm người (" + automaticDetails
                + (manualDetails.isEmpty() ? "" : "; Phụ thu phát sinh: " + manualDetails) + ")";
        lines.add(new InvoiceData.LineItem("EXTRA_PERSON", description, automatic.amount().add(manualAmount)));
    }

    private record AutomaticSurcharge(BigDecimal amount, int extraGuestCount, long unitPrice, long nights) {}

    private static void addSurchargeLine(List<InvoiceData.LineItem> lines, List<BookingSurcharge> surcharges,
            SurchargeType type, String code, String label) {
        List<BookingSurcharge> matching = surcharges.stream()
                .filter(line -> line.getSurchargeType() == type).toList();
        BigDecimal amount = matching.stream().map(line -> BigDecimal.valueOf(line.getAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String details = java.util.stream.IntStream.range(0, matching.size())
                .mapToObj(index -> surchargeDescription(index + 1, matching.get(index)))
                .collect(java.util.stream.Collectors.joining("; "));
        lines.add(new InvoiceData.LineItem(code, details.isEmpty() ? label : label + " (" + details + ")", amount));
    }
    private static String surchargeDescription(int row, BookingSurcharge surcharge) {
        String detail = "#" + row + ": quantity " + surcharge.getQuantity() + ", unit price "
                + surcharge.getUnitPrice() + ", nights " + surcharge.getNights() + ", amount "
                + surcharge.getAmount();
        if (surcharge.getPriceOverrideReason() != null && !surcharge.getPriceOverrideReason().isBlank()) {
            detail += " (default unit price " + surcharge.getDefaultUnitPrice() + "; reason: "
                    + surcharge.getPriceOverrideReason().trim() + ")";
        }
        return detail;
    }
    private static BigDecimal nonNegative(BigDecimal amount, String field) {
        if (amount == null || amount.signum() < 0) {
            throw error(HttpStatus.CONFLICT, "Settlement thiếu " + field + " hợp lệ.");
        }
        return amount;
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw error(HttpStatus.CONFLICT, message);
    }

    private static boolean isBlank(String value) { return value == null || value.isBlank(); }

    private static OperatingSettings withCurrentContactFallback(OperatingSettings historical, OperatingSettings current) {
        OperatingSettings invoiceSettings = new OperatingSettings();
        invoiceSettings.setHomestayName(historical.getHomestayName());
        invoiceSettings.setAddress(isBlank(historical.getAddress()) ? current.getAddress() : historical.getAddress());
        invoiceSettings.setPhone(isBlank(historical.getPhone()) ? current.getPhone() : historical.getPhone());
        return invoiceSettings;
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}
