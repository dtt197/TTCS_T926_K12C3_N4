package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.booking.BookingCancellationResponse;
import com.ttcs.homestay.dto.booking.CancelBookingRequest;
import com.ttcs.homestay.dto.booking.CancellationPreviewResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingDeposit;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.CancelReason;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.repository.BookingDepositAdjustmentRepository;
import com.ttcs.homestay.repository.BookingDepositRepository;
import com.ttcs.homestay.repository.BookingRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * S3-05: lễ tân huỷ booking chưa nhận phòng, hệ thống tính hoàn cọc.
 * Số tiền hoàn luôn do hệ thống tính lại lúc xác nhận, không nhận từ giao diện.
 */
@Service
public class BookingCancellationService {

    private static final Set<BookingStatus> CANCELLABLE =
            EnumSet.of(BookingStatus.CHO_XAC_NHAN, BookingStatus.DA_XAC_NHAN);

    private final BookingRepository bookingRepository;
    private final OperatingSettingsService settingsService;
    private final BookingDepositRepository depositRepository;
    private final BookingDepositAdjustmentRepository adjustmentRepository;

    public BookingCancellationService(
            BookingRepository bookingRepository,
            OperatingSettingsService settingsService,
            BookingDepositRepository depositRepository,
            BookingDepositAdjustmentRepository adjustmentRepository) {
        this.bookingRepository = bookingRepository;
        this.settingsService = settingsService;
        this.depositRepository = depositRepository;
        this.adjustmentRepository = adjustmentRepository;
    }

    private record Quote(
            OperatingSettings settings,
            long depositAmount,
            long hoursBeforeCheckIn,
            CancellationRefundCalculator.Result refund) {
    }

    @Transactional(readOnly = true)
    public CancellationPreviewResponse preview(String bookingCode) {
        Booking booking = findCancellable(bookingCode);
        Quote quote = quote(booking, OffsetDateTime.now());
        return new CancellationPreviewResponse(
                booking.getBookingCode(),
                booking.getGuestName(),
                booking.getRoomTypeNameSnapshot(),
                booking.getStatus(),
                booking.getCheckInDate(),
                quote.settings().getCheckInTime(),
                quote.hoursBeforeCheckIn(),
                quote.depositAmount(),
                quote.refund().appliedTierHours(),
                quote.refund().refundPercent(),
                quote.refund().refundAmount());
    }

    @Transactional
    public BookingCancellationResponse cancel(String bookingCode, CancelBookingRequest request) {
        if (request == null || request.reason() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng chọn lý do huỷ");
        }
        String note = request.note() == null || request.note().isBlank()
                ? null
                : request.note().trim();
        if (request.reason() == CancelReason.LY_DO_KHAC && note == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Vui lòng nhập ghi chú khi chọn lý do khác");
        }

        Booking booking = findCancellable(bookingCode);
        OffsetDateTime now = OffsetDateTime.now();
        Quote quote = quote(booking, now);

        booking.setStatus(BookingStatus.DA_HUY);
        booking.setCancelReason(request.reason().name());
        booking.setCancelNote(note);
        booking.setCancelDepositAmount(quote.depositAmount());
        booking.setCancelRefundPercent(quote.refund().refundPercent());
        booking.setCancelRefundAmount(quote.refund().refundAmount());
        booking.setCancelledBy(currentActor());
        booking.setCancelledAt(now);
        Booking saved = bookingRepository.save(booking);

        return new BookingCancellationResponse(
                saved.getBookingCode(),
                saved.getStatus(),
                saved.getCancelReason(),
                saved.getCancelNote(),
                saved.getCancelDepositAmount(),
                saved.getCancelRefundPercent(),
                saved.getCancelRefundAmount());
    }

    private Booking findCancellable(String bookingCode) {
        String code = bookingCode == null ? "" : bookingCode.trim().toUpperCase(Locale.ROOT);
        Booking booking = bookingRepository.findByBookingCode(code)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy booking"));
        if (booking.getStatus() == BookingStatus.DA_HUY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Booking đã được huỷ trước đó");
        }
        if (booking.getStatus() == BookingStatus.DA_NHAN_PHONG) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Booking đã nhận phòng không huỷ được, vui lòng làm thủ tục trả phòng sớm");
        }
        if (!CANCELLABLE.contains(booking.getStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Booking ở trạng thái hiện tại không thể huỷ");
        }
        return booking;
    }

    private Quote quote(Booking booking, OffsetDateTime at) {
        OperatingSettings settings = settingsService.findEffectiveAt(booking.getCreatedAt());
        long hours = CancellationRefundCalculator.hoursUntilCheckIn(
                at, booking.getCheckInDate(), settings.getCheckInTime());
        long deposit = recordedDeposit(booking);
        return new Quote(
                settings,
                deposit,
                hours,
                CancellationRefundCalculator.calculate(
                        settings.getCancellationTiers(), hours, deposit));
    }

    /** Tiền cọc đã ghi nhận = cọc gốc (S3-01) cộng/trừ các bút toán điều chỉnh. */
    private long recordedDeposit(Booking booking) {
        BigDecimal original = depositRepository.findByBooking(booking)
                .map(BookingDeposit::getAmount)
                .orElse(BigDecimal.ZERO);
        BigDecimal adjustments = adjustmentRepository.sumSignedAmountsByBookingId(booking.getId());
        BigDecimal total = original.add(adjustments == null ? BigDecimal.ZERO : adjustments);
        return total.max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP).longValue();
    }

    /** Người thực hiện huỷ: email trong JWT, nếu không có thì tên đăng nhập. */
    private static String currentActor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        if (authentication instanceof JwtAuthenticationToken token) {
            String email = token.getToken().getClaimAsString("email");
            if (email != null && !email.isBlank()) {
                return email;
            }
        }
        return authentication.getName();
    }
}