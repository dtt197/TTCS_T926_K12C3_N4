package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.booking.BookingConfirmRequest;
import com.ttcs.homestay.dto.booking.BookingDepositResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingDeposit;
import com.ttcs.homestay.exception.DuplicatePaymentException;
import com.ttcs.homestay.exception.InvalidDepositException;
import com.ttcs.homestay.repository.BookingDepositAdjustmentRepository;
import com.ttcs.homestay.repository.BookingDepositRepository;
import com.ttcs.homestay.repository.BookingRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.ttcs.homestay.dto.booking.BookingConfirmResponse;
@Service
public class BookingDepositService {

    private final BookingDepositRepository bookingDepositRepository;
    private final BookingRepository bookingRepository;
    private final BookingDepositAdjustmentRepository adjustmentRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public BookingDepositService(
            BookingDepositRepository bookingDepositRepository,
            BookingRepository bookingRepository,
            BookingDepositAdjustmentRepository adjustmentRepository) {
        this.bookingDepositRepository = bookingDepositRepository;
        this.bookingRepository = bookingRepository;
        this.adjustmentRepository = adjustmentRepository;
    }

    public BookingDepositService(
            BookingDepositRepository bookingDepositRepository,
            BookingRepository bookingRepository) {
        this(bookingDepositRepository, bookingRepository, null);
    }

    @Transactional(readOnly = true)
    public boolean hasDeposit(Booking booking) {
        return bookingDepositRepository.existsByBooking(booking);
    }
    
    /**
     * S3-04 AC3: tổng tiền cọc đã ghi nhận hiện tại = cọc gốc cộng/trừ các bút toán điều chỉnh; 0 nếu chưa có cọc.
     * Chỉ đọc, không thay đổi bản ghi cọc.
     */
    @Transactional(readOnly = true)
    public long currentDepositTotal(Booking booking) {
        BookingDeposit deposit = bookingDepositRepository.findByBooking(booking).orElse(null);
        if (deposit == null) {
            return 0L;
        }
        BigDecimal total = deposit.getAmount();
        if (adjustmentRepository != null && booking.getId() != null) {
            BigDecimal adjustments = adjustmentRepository.sumSignedAmountsByBookingId(booking.getId());
            if (adjustments != null) {
                total = total.add(adjustments);
            }
        }
        return total.setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }

    @Transactional(readOnly = true)
    public BigDecimal currentDepositTotalExact(Booking booking) {
        BookingDeposit deposit = bookingDepositRepository.findByBooking(booking).orElse(null);
        if (deposit == null) return BigDecimal.ZERO;
        BigDecimal total = deposit.getAmount();
        if (adjustmentRepository != null && booking.getId() != null) {
            BigDecimal adjustments = adjustmentRepository.sumSignedAmountsByBookingId(booking.getId());
            if (adjustments != null) total = total.add(adjustments);
        }
        return total;
    }

    @Transactional
    public BookingConfirmResponse confirmBooking(Long id, BookingConfirmRequest request, String createdBy) {
        Booking booking = bookingRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy booking"));

        if (booking.getStatus() != com.ttcs.homestay.entity.BookingStatus.CHO_XAC_NHAN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking không ở trạng thái chờ xác nhận, không thể xác nhận tiền cọc");
        }

        var now = java.time.OffsetDateTime.now();
        if ((booking.getHoldExpiresAt() != null && !booking.getHoldExpiresAt().isAfter(now))
                || BookingHoldPolicy.isExpired(booking.getStatus(),
                        booking.getCreatedAt() == null ? null : booking.getCreatedAt().toInstant(), now.toInstant())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Đặt phòng đã hết hạn giữ chỗ, không thể xác nhận");
        }
        if (request == null) {
            throw new InvalidDepositException("Vui lòng nhập thông tin tiền cọc");
        }
        validateDeposit(request.amount(), request.paymentMethod(), request.paymentReference());
        if (request.amount().compareTo(BigDecimal.valueOf(booking.getTotalAmount())) > 0) {
            throw new InvalidDepositException("Tiền cọc không được lớn hơn tổng tiền đặt phòng");
        }
        if (request.receivedDate() == null) {
            throw new InvalidDepositException("Ngày nhận cọc là bắt buộc");
        }

        // Lát 1: mỗi booking chỉ có tối đa 1 khoản cọc
        if (bookingDepositRepository.existsByBooking(booking)) {
            throw new DuplicatePaymentException("Đã có tiền cọc cho booking này");
        }

        BookingDeposit savedDeposit = recordOriginalDeposit(
                booking, request.amount(), request.paymentMethod(), request.receivedDate(),
                request.paymentReference(), createdBy);

        // Ghi nhận cọc + chuyển booking sang DA_XAC_NHAN + xóa holdExpiresAt
        booking.setStatus(com.ttcs.homestay.entity.BookingStatus.DA_XAC_NHAN);
        booking.setHoldExpiresAt(null);

        booking = bookingRepository.save(booking);

        return BookingConfirmResponse.from(
        booking,
        new BookingDepositResponse(
                savedDeposit.getId(),
                savedDeposit.getAmount(),
                savedDeposit.getPaymentMethod(),
                savedDeposit.getReceivedDate(),
                savedDeposit.getPaymentReference(),
                savedDeposit.getReservationCode(),
                savedDeposit.getCreatedBy(),
                savedDeposit.getCreatedAt()
        )
);
    }

    @Transactional
    public BookingDeposit recordOriginalDeposit(Booking booking, BigDecimal amount, String paymentMethod,
            java.time.LocalDate receivedDate, String paymentReference, String createdBy) {
        validateDeposit(amount, paymentMethod, paymentReference);
        if (amount.compareTo(BigDecimal.valueOf(booking.getTotalAmount())) > 0) {
            throw new InvalidDepositException("Tiá»n cá»c khÃ´ng Ä‘Æ°á»£c lá»›n hÆ¡n tá»•ng tiá»n Ä‘áº·t phÃ²ng");
        }
        if (receivedDate == null) {
            throw new InvalidDepositException("NgÃ y nháº­n cá»c lÃ  báº¯t buá»™c");
        }
        if (bookingDepositRepository.existsByBooking(booking)) {
            throw new DuplicatePaymentException("ÄÃ£ cÃ³ tiá»n cá»c cho booking nÃ y");
        }
        BookingDeposit deposit = new BookingDeposit();
        deposit.setBooking(booking);
        deposit.setAmount(amount);
        deposit.setPaymentMethod(paymentMethod.trim().toUpperCase(java.util.Locale.ROOT));
        deposit.setReceivedDate(receivedDate);
        deposit.setPaymentReference(normalizePaymentReference(paymentReference));
        deposit.setCreatedBy(createdBy);
        deposit.setCreatedAt(OffsetDateTime.now());
        return bookingDepositRepository.save(deposit);
    }

    public void validateDeposit(BigDecimal amount, String paymentMethod, String paymentReference) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidDepositException("Số tiền cọc phải dương");
        }

        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new InvalidDepositException("Phương thức thanh toán không được để trống");
        }

        String method = paymentMethod.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"CASH".equals(method) && !"BANK_TRANSFER".equals(method)) {
            throw new InvalidDepositException("Phương thức thanh toán không hợp lệ");
        }
        if ("BANK_TRANSFER".equals(method)) {
            if (paymentReference == null || paymentReference.isBlank()) {
                throw new InvalidDepositException("Mã tham chiếu không được trống khi thanh toán chuyển khoản");
            }
        }
    }

    private String normalizePaymentReference(String paymentReference) {
        if (paymentReference == null) {
            return null;
        }
        String trimmed = paymentReference.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }


}
