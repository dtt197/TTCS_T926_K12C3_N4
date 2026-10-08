package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.booking.BookingDepositAdjustmentRequest;
import com.ttcs.homestay.dto.booking.BookingDepositAdjustmentResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingDeposit;
import com.ttcs.homestay.entity.BookingDepositAdjustment;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.DepositAdjustmentType;
import com.ttcs.homestay.exception.InvalidDepositException;
import com.ttcs.homestay.repository.BookingDepositAdjustmentRepository;
import com.ttcs.homestay.repository.BookingDepositRepository;
import com.ttcs.homestay.repository.BookingRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BookingDepositAdjustmentService {

    private final BookingRepository bookingRepository;
    private final BookingDepositRepository bookingDepositRepository;
    private final BookingDepositAdjustmentRepository adjustmentRepository;

    public BookingDepositAdjustmentService(
            BookingRepository bookingRepository,
            BookingDepositRepository bookingDepositRepository,
            BookingDepositAdjustmentRepository adjustmentRepository) {
        this.bookingRepository = bookingRepository;
        this.bookingDepositRepository = bookingDepositRepository;
        this.adjustmentRepository = adjustmentRepository;
    }

    @Transactional
    public BookingDepositAdjustmentResponse createAdjustment(Long bookingId, BookingDepositAdjustmentRequest request) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy booking"));

        if (booking.getStatus() != BookingStatus.DA_XAC_NHAN) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Chỉ booking đã xác nhận mới được điều chỉnh tiền cọc");
        }

        if (request == null) {
            throw new InvalidDepositException("Vui lòng nhập thông tin điều chỉnh tiền cọc");
        }
        validateRequest(request);

        BookingDeposit originalDeposit = bookingDepositRepository.findByBooking(booking)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Booking chưa có khoản cọc gốc"));

        String actor = currentActorName();
        BigDecimal currentTotal = calculateCurrentDepositTotal(bookingId, originalDeposit);
        BigDecimal delta = request.adjustmentType() == DepositAdjustmentType.TANG
                ? request.amount()
                : request.amount().negate();
        BigDecimal adjustedTotal = currentTotal.add(delta);

        if (adjustedTotal.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidDepositException("Tổng tiền cọc sau điều chỉnh không được nhỏ hơn 0");
        }
        if (adjustedTotal.compareTo(BigDecimal.valueOf(booking.getTotalAmount())) > 0) {
            throw new InvalidDepositException("Tổng tiền cọc sau điều chỉnh không được lớn hơn tổng tiền booking");
        }

        BookingDepositAdjustment adjustment = new BookingDepositAdjustment();
        adjustment.setBooking(booking);
        adjustment.setAdjustmentType(request.adjustmentType());
        adjustment.setAmount(request.amount());
        adjustment.setReason(request.reason().trim());
        adjustment.setCreatedBy(actor);
        adjustment.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        BookingDepositAdjustment saved = adjustmentRepository.save(adjustment);

        return BookingDepositAdjustmentResponse.from(saved, bookingId, adjustedTotal);
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateCurrentDepositTotal(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy booking"));
        BookingDeposit originalDeposit = bookingDepositRepository.findByBooking(booking)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Booking chưa có khoản cọc gốc"));
        return calculateCurrentDepositTotal(bookingId, originalDeposit);
    }

    private BigDecimal calculateCurrentDepositTotal(Long bookingId, BookingDeposit originalDeposit) {
        BigDecimal signedAdjustments = adjustmentRepository.sumSignedAmountsByBookingId(bookingId);
        if (signedAdjustments == null) {
            signedAdjustments = BigDecimal.ZERO;
        }
        return originalDeposit.getAmount().add(signedAdjustments);
    }

    private void validateRequest(BookingDepositAdjustmentRequest request) {
        if (request.adjustmentType() == null) {
            throw new InvalidDepositException("Loại điều chỉnh là bắt buộc");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidDepositException("Số tiền điều chỉnh phải lớn hơn 0");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new InvalidDepositException("Lý do điều chỉnh là bắt buộc");
        }
        if (request.reason().length() > 500) {
            throw new InvalidDepositException("Lý do điều chỉnh không được vượt quá 500 ký tự");
        }
    }

    private String currentActorName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Không xác định được người thực hiện từ JWT");
        }

        String actorName = token.getToken().getClaimAsString("fullName");
        if (actorName == null || actorName.isBlank()) {
            actorName = token.getToken().getClaimAsString("email");
        }
        if (actorName == null || actorName.isBlank()) {
            actorName = authentication.getName();
        }
        if (actorName == null || actorName.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "JWT không có thông tin người thực hiện hợp lệ");
        }
        return actorName;
    }
}
