package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.booking.BookingCancellationResponse;
import com.ttcs.homestay.dto.booking.CancelBookingRequest;
import com.ttcs.homestay.dto.booking.CancellationPreviewResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.CancelReason;
import com.ttcs.homestay.entity.OperatingSettings;
import com.ttcs.homestay.repository.BookingRepository;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * S3-05 Lát 1: lễ tân huỷ booking chưa nhận phòng, hệ thống tính hoàn cọc.
 * Số tiền hoàn luôn do hệ thống tính lại lúc xác nhận, không nhận từ giao diện.
 */
@Service
public class BookingCancellationService {

    private static final Set<BookingStatus> CANCELLABLE =
            EnumSet.of(BookingStatus.CHO_XAC_NHAN, BookingStatus.DA_XAC_NHAN);

    private final BookingRepository bookingRepository;
    private final OperatingSettingsService settingsService;

    public BookingCancellationService(
            BookingRepository bookingRepository,
            OperatingSettingsService settingsService) {
        this.bookingRepository = bookingRepository;
        this.settingsService = settingsService;
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
        Quote quote = quote(booking, OffsetDateTime.now());

        booking.setStatus(BookingStatus.DA_HUY);
        booking.setCancelReason(request.reason().name());
        booking.setCancelNote(note);
        booking.setCancelDepositAmount(quote.depositAmount());
        booking.setCancelRefundPercent(quote.refund().refundPercent());
        booking.setCancelRefundAmount(quote.refund().refundAmount());
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

    /** Tiền cọc đã ghi nhận. Hiện bằng 0 vì story lễ tân ghi nhận cọc (Sprint 3) chưa có; thay hàm này khi có. */
    private static long recordedDeposit(Booking booking) {
        return 0L;
    }
}