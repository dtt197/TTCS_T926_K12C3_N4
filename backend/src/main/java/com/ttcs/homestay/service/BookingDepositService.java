package com.ttcs.homestay.service;

import com.ttcs.homestay.dto.booking.BookingConfirmRequest;
import com.ttcs.homestay.dto.booking.BookingDepositResponse;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingDeposit;
import com.ttcs.homestay.exception.DuplicatePaymentException;
import com.ttcs.homestay.exception.InvalidDepositException;
import com.ttcs.homestay.repository.BookingDepositRepository;
import com.ttcs.homestay.repository.BookingRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.ttcs.homestay.dto.booking.BookingConfirmResponse;
@Service
public class BookingDepositService {

    private final BookingDepositRepository bookingDepositRepository;
    private final BookingRepository bookingRepository;

    public BookingDepositService(
            BookingDepositRepository bookingDepositRepository,
            BookingRepository bookingRepository) {
        this.bookingDepositRepository = bookingDepositRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional
    public BookingConfirmResponse confirmBooking(Long id, BookingConfirmRequest request) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Không tìm thấy booking"));

        if (booking.getStatus() != com.ttcs.homestay.entity.BookingStatus.CHO_XAC_NHAN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Booking không ở trạng thái chờ xác nhận, không thể xác nhận tiền cọc");
        }

        validateDeposit(request.amount(), request.paymentMethod(), request.paymentReference());
        if (request.receivedDate() == null) {
            throw new InvalidDepositException("Ngay nhan coc la bat buoc");
        }

        // Lát 1: mỗi booking chỉ có tối đa 1 khoản cọc
        if (bookingDepositRepository.existsByBooking(booking)) {
            throw new DuplicatePaymentException("Đã có tiền cọc cho booking này");
        }

        BookingDeposit deposit = new BookingDeposit();
        deposit.setBooking(booking);
        deposit.setAmount(request.amount());
        deposit.setPaymentMethod(request.paymentMethod().trim().toUpperCase(java.util.Locale.ROOT));
        deposit.setReceivedDate(request.receivedDate());
        deposit.setPaymentReference(normalizePaymentReference(request.paymentReference()));
        deposit.setCreatedBy(request.createdBy());

        BookingDeposit savedDeposit = bookingDepositRepository.save(deposit);

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
                savedDeposit.getCreatedAt()
        )
);
    }

    private void validateDeposit(BigDecimal amount, String paymentMethod, String paymentReference) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidDepositException("Số tiền cọc phải dương");
        }

        if (paymentMethod == null || paymentMethod.isBlank()) {
            throw new InvalidDepositException("Phương thức thanh toán không được để trống");
        }

        String method = paymentMethod.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"CASH".equals(method) && !"BANK_TRANSFER".equals(method)) {
            throw new InvalidDepositException("Phuong thuc thanh toan khong hop le");
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