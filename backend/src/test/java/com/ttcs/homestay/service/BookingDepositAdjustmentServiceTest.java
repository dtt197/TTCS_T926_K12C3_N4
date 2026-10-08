package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.booking.BookingDepositAdjustmentRequest;
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
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class BookingDepositAdjustmentServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingDepositRepository depositRepository;
    @Mock private BookingDepositAdjustmentRepository adjustmentRepository;

    private BookingDepositAdjustmentService service;
    private Booking booking;
    private BookingDeposit originalDeposit;

    @BeforeEach
    void setUp() {
        service = new BookingDepositAdjustmentService(bookingRepository, depositRepository, adjustmentRepository);
        booking = new Booking();
        booking.setId(7L);
        booking.setStatus(BookingStatus.DA_XAC_NHAN);
        booking.setTotalAmount(1_000_000L);
        originalDeposit = new BookingDeposit();
        originalDeposit.setId(14L);
        originalDeposit.setBooking(booking);
        originalDeposit.setAmount(new BigDecimal("400.0000"));
        originalDeposit.setCreatedBy("Original actor");
        originalDeposit.setCreatedAt(OffsetDateTime.parse("2026-01-01T00:00:00Z"));

        setJwtActor();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void increaseCreatesImmutableLedgerEntryWithJwtActorAndUtcTime() {
        stubConfirmedBookingWithOriginalDeposit();
        when(adjustmentRepository.sumSignedAmountsByBookingId(7L)).thenReturn(BigDecimal.ZERO);
        when(adjustmentRepository.save(any(BookingDepositAdjustment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createAdjustment(7L, request(DepositAdjustmentType.TANG, "125.2500", "Add deposit"));

        assertThat(response.adjustmentType()).isEqualTo(DepositAdjustmentType.TANG);
        assertThat(response.currentDepositTotal()).isEqualByComparingTo("525.2500");
        assertThat(response.createdBy()).isEqualTo("JWT Staff");
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.createdAt().getOffset()).isEqualTo(ZoneOffset.UTC);
        verify(adjustmentRepository).save(any(BookingDepositAdjustment.class));
        assertOriginalDepositUnchanged();
    }

    @Test
    void decreaseCreatesLedgerEntryAndComputesTotal() {
        stubConfirmedBookingWithOriginalDeposit();
        when(adjustmentRepository.sumSignedAmountsByBookingId(7L)).thenReturn(new BigDecimal("100.0000"));
        when(adjustmentRepository.save(any(BookingDepositAdjustment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createAdjustment(7L, request(DepositAdjustmentType.GIAM, "250.0000", "Refund"));

        assertThat(response.currentDepositTotal()).isEqualByComparingTo("250.0000");
        verify(adjustmentRepository).save(any(BookingDepositAdjustment.class));
        assertOriginalDepositUnchanged();
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking));
        assertInvalid(request(DepositAdjustmentType.TANG, "0", "Reason"));
        assertInvalid(request(DepositAdjustmentType.GIAM, "-1", "Reason"));
    }

    @Test
    void rejectsMissingOrOverlongReason() {
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking));
        assertInvalid(new BookingDepositAdjustmentRequest(DepositAdjustmentType.TANG,
                BigDecimal.ONE, "  "));
        assertInvalid(new BookingDepositAdjustmentRequest(DepositAdjustmentType.TANG,
                BigDecimal.ONE, "x".repeat(501)));
    }

    @Test
    void rejectsMissingBooking() {
        when(bookingRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAdjustment(99L,
                request(DepositAdjustmentType.TANG, "1", "Reason")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Không tìm thấy booking");
        verify(adjustmentRepository, never()).save(any());
    }

    @Test
    void rejectsBookingWithoutOriginalDeposit() {
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking));
        when(depositRepository.findByBooking(booking)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAdjustment(7L,
                request(DepositAdjustmentType.TANG, "1", "Reason")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("chưa có khoản cọc gốc");
        verify(adjustmentRepository, never()).save(any());
    }

    @Test
    void allowsAdjustmentsOnlyForConfirmedBooking() {
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking));
        booking.setStatus(BookingStatus.DA_NHAN_PHONG);

        assertThatThrownBy(() -> service.createAdjustment(7L,
                request(DepositAdjustmentType.TANG, "1", "Reason")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("đã xác nhận");
        verify(adjustmentRepository, never()).save(any());
    }

    @Test
    void rejectsDecreaseBelowZero() {
        stubConfirmedBookingWithOriginalDeposit();
        when(adjustmentRepository.sumSignedAmountsByBookingId(7L)).thenReturn(BigDecimal.ZERO);
        assertInvalid(request(DepositAdjustmentType.GIAM, "400.0001", "Too much"));
        verify(adjustmentRepository, never()).save(any());
    }

    @Test
    void rejectsIncreaseAboveBookingTotal() {
        stubConfirmedBookingWithOriginalDeposit();
        BigDecimal originalAmount = originalDeposit.getAmount();
        BigDecimal previousAdjustments = new BigDecimal("999600.0000");
        BigDecimal newAdjustment = new BigDecimal("401.0000");
        BigDecimal bookingTotal = BigDecimal.valueOf(booking.getTotalAmount());
        when(adjustmentRepository.sumSignedAmountsByBookingId(7L)).thenReturn(previousAdjustments);

        assertThat(originalAmount).isEqualByComparingTo("400.0000");
        assertThat(originalAmount.add(previousAdjustments).add(newAdjustment))
                .isEqualByComparingTo("1000401.0000")
                .isGreaterThan(bookingTotal);
        assertInvalid(request(DepositAdjustmentType.TANG, newAdjustment.toPlainString(), "Too much"));
        verify(adjustmentRepository, never()).save(any());
        assertOriginalDepositUnchanged();
    }

    @Test
    void whenCurrentTotalExceedsBookingAllowsOnlyDecreaseIntoValidRange() {
        stubConfirmedBookingWithOriginalDeposit();
        booking.setTotalAmount(450L);
        when(adjustmentRepository.sumSignedAmountsByBookingId(7L)).thenReturn(new BigDecimal("100.0000"));
        when(adjustmentRepository.save(any(BookingDepositAdjustment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createAdjustment(7L, request(DepositAdjustmentType.GIAM, "50.0000", "Reduce excess"));

        assertThat(response.currentDepositTotal()).isEqualByComparingTo("450.0000");
        verify(adjustmentRepository).save(any(BookingDepositAdjustment.class));
    }

    @Test
    void multipleAdjustmentsUseExactSignedTotal() {
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking));
        when(bookingRepository.findById(7L)).thenReturn(Optional.of(booking));
        when(depositRepository.findByBooking(booking)).thenReturn(Optional.of(originalDeposit));
        when(adjustmentRepository.sumSignedAmountsByBookingId(7L)).thenReturn(new BigDecimal("25.1250"));
        when(adjustmentRepository.save(any(BookingDepositAdjustment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.createAdjustment(7L, request(DepositAdjustmentType.GIAM, "10.0050", "Fine adjustment"));

        assertThat(response.currentDepositTotal()).isEqualByComparingTo("415.1200");
        assertThat(service.calculateCurrentDepositTotal(7L)).isEqualByComparingTo("425.1250");
    }

    @Test
    void databaseSaveFailurePropagatesAndOriginalDepositRemainsUnchanged() {
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking));
        when(depositRepository.findByBooking(booking)).thenReturn(Optional.of(originalDeposit));
        when(adjustmentRepository.sumSignedAmountsByBookingId(7L)).thenReturn(BigDecimal.ZERO);
        when(adjustmentRepository.save(any(BookingDepositAdjustment.class)))
                .thenThrow(new IllegalStateException("database failure"));

        assertThatThrownBy(() -> service.createAdjustment(7L,
                request(DepositAdjustmentType.TANG, "10", "Reason")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database failure");
        assertOriginalDepositUnchanged();
    }

    @Test
    void requiresJwtActorAndDoesNotAcceptAnUnauthenticatedPrincipal() {
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking));
        when(depositRepository.findByBooking(booking)).thenReturn(Optional.of(originalDeposit));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("spoofed actor", "password"));

        assertThatThrownBy(() -> service.createAdjustment(7L,
                request(DepositAdjustmentType.TANG, "10", "Reason")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("JWT");
        verify(adjustmentRepository, never()).save(any());
    }

    private void assertInvalid(BookingDepositAdjustmentRequest request) {
        assertThatThrownBy(() -> service.createAdjustment(7L, request))
                .isInstanceOf(InvalidDepositException.class);
    }

    private void stubConfirmedBookingWithOriginalDeposit() {
        when(bookingRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(booking));
        when(depositRepository.findByBooking(booking)).thenReturn(Optional.of(originalDeposit));
    }

    private static BookingDepositAdjustmentRequest request(DepositAdjustmentType type, String amount, String reason) {
        return new BookingDepositAdjustmentRequest(type, new BigDecimal(amount), reason);
    }

    private void setJwtActor() {
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject("12")
                .claim("fullName", "JWT Staff")
                .claim("email", "staff@example.test")
                .issuedAt(OffsetDateTime.now().toInstant())
                .expiresAt(OffsetDateTime.now().plusHours(1).toInstant())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private void assertOriginalDepositUnchanged() {
        assertThat(originalDeposit.getAmount()).isEqualByComparingTo("400.0000");
        assertThat(originalDeposit.getCreatedBy()).isEqualTo("Original actor");
        assertThat(originalDeposit.getCreatedAt()).isEqualTo(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
    }
}
