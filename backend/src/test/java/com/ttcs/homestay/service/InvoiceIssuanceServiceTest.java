package com.ttcs.homestay.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.ttcs.homestay.dto.InvoiceData;
import com.ttcs.homestay.dto.InvoiceCancellationRequest;
import com.ttcs.homestay.entity.*;
import com.ttcs.homestay.repository.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class InvoiceIssuanceServiceTest {
    @Mock BookingRepository bookingRepository;
    @Mock IssuedInvoiceRepository invoiceRepository;
    @Mock InvoiceNumberCounterRepository counterRepository;
    @Mock UserRepository userRepository;
    @Mock InvoiceDataService invoiceDataService;
    @Mock InvoicePdfService pdfService;
    @Mock tools.jackson.databind.ObjectMapper objectMapper;
    @Mock PlatformTransactionManager transactionManager;

    private Booking booking;
    private User actor;
    private InvoiceNumberCounter counter;
    private InvoiceData data;
    private byte[] pdf;

    @BeforeEach
    void setUp() {
        booking = new Booking(); booking.setId(44L);
        actor = mock(User.class); lenient().when(actor.getFullName()).thenReturn("Issuer Snapshot"); lenient().when(actor.isActive()).thenReturn(true);
        counter = new InvoiceNumberCounter();
        data = new InvoiceData(44L, "BK-44", "Guest at issuance", "0900", null, null,
                new InvoiceData.Homestay("Homestay snapshot", "Address", "Phone", null, null),
                new InvoiceData.Room(12L, "201", "Standard"), LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 16), 1, List.of(), java.math.BigDecimal.ZERO,
                java.math.BigDecimal.ZERO, BookingSettlement.SettlementType.ZERO,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO,
                OffsetDateTime.parse("2026-10-16T00:00:00+07:00"), false);
        pdf = "%PDF-test-immutable".getBytes(StandardCharsets.UTF_8);
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenAnswer(invocation -> mock(TransactionStatus.class));
        lenient().when(bookingRepository.findByIdForUpdate(44L)).thenReturn(Optional.of(booking));
        lenient().when(invoiceRepository.findByBookingId(44L)).thenReturn(Optional.empty());
        lenient().when(userRepository.findWithRoleById(9L)).thenReturn(Optional.of(actor));
        lenient().when(invoiceDataService.getInvoiceData(44L)).thenReturn(data);
        lenient().when(counterRepository.findByYearForUpdate(any(Short.class))).thenReturn(Optional.of(counter));
        lenient().when(pdfService.generate(any())).thenReturn(pdf);
        lenient().when(objectMapper.convertValue(any(), any(tools.jackson.core.type.TypeReference.class)))
                .thenAnswer(invocation -> Map.of("guestName", ((InvoiceData) invocation.getArgument(0)).guestName(),
                        "invoiceNumber", ((InvoiceData) invocation.getArgument(0)).invoiceNumber(),
                        "issuedDate", ((InvoiceData) invocation.getArgument(0)).issuedDate().toString()));
        lenient().when(invoiceRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        setJwtSubject("9");
    }

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    private void setJwtSubject(String subject) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject)
                .issuedAt(Instant.parse("2026-10-15T00:00:00Z"))
                .expiresAt(Instant.parse("2027-10-15T00:00:00Z")).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private InvoiceIssuanceService service(Instant now) {
        return new InvoiceIssuanceService(bookingRepository, invoiceRepository, counterRepository, userRepository,
                invoiceDataService, pdfService, objectMapper, Clock.fixed(now, ZoneOffset.UTC), transactionManager);
    }

    @Test void allocatesFirstAndNextNumberAndPersistsSnapshotMetadataAndDigest() throws Exception {
        InvoiceIssuanceService service = service(Instant.parse("2026-10-15T17:00:00Z"));
        IssuedInvoice first = service.issueOrGet(44L);
        assertThat(first.getInvoiceNumber()).isEqualTo("2026-000001");
        assertThat(first.getInvoiceYear()).isEqualTo((short) 2026);
        assertThat(first.getSequenceNumber()).isEqualTo(1);
        assertThat(first.getIssuedAt()).isEqualTo(OffsetDateTime.parse("2026-10-16T00:00:00+07:00"));
        assertThat(first.getIssuedByUser()).isSameAs(actor);
        assertThat(first.getIssuedByName()).isEqualTo("Issuer Snapshot");
        assertThat(first.getPdfBytes()).containsExactly(pdf);
        assertThat(first.getPdfSha256()).isEqualTo(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(pdf)));
        assertThat(first.getSnapshotJson()).containsEntry("guestName", "Guest at issuance")
                .containsEntry("invoiceNumber", "2026-000001").containsEntry("issuedDate", "2026-10-16");

        when(invoiceRepository.findByBookingId(44L)).thenReturn(Optional.empty());
        IssuedInvoice second = service(Instant.parse("2026-10-16T17:00:00Z")).issueOrGet(44L);
        assertThat(second.getInvoiceNumber()).isEqualTo("2026-000002");
    }

    @Test void derivesYearFromVietnamZoneBeforeAndAfterLocalMidnight() {
        counter.setLastNumber(12);
        IssuedInvoice beforeLocalNewYear = service(Instant.parse("2026-12-31T16:59:59Z")).issueOrGet(44L);
        assertThat(beforeLocalNewYear.getInvoiceNumber()).isEqualTo("2026-000013");
        assertThat(beforeLocalNewYear.getIssuedAt().getYear()).isEqualTo(2026);
        when(invoiceRepository.findByBookingId(44L)).thenReturn(Optional.empty());
        IssuedInvoice afterLocalNewYear = service(Instant.parse("2026-12-31T17:00:00Z")).issueOrGet(44L);
        assertThat(afterLocalNewYear.getInvoiceNumber()).isEqualTo("2027-000014");
        assertThat(afterLocalNewYear.getInvoiceYear()).isEqualTo((short) 2027);
    }

    @Test void firstInvoiceOfNewYearStartsAtOneRegardlessOfPreviousYearCounter() {
        InvoiceNumberCounter previousYearCounter = new InvoiceNumberCounter();
        previousYearCounter.setInvoiceYear((short) 2026);
        previousYearCounter.setLastNumber(845);
        InvoiceNumberCounter newYearCounter = new InvoiceNumberCounter();
        newYearCounter.setInvoiceYear((short) 2027);
        newYearCounter.setLastNumber(0);
        when(counterRepository.findByYearForUpdate((short) 2026)).thenReturn(Optional.of(previousYearCounter));
        when(counterRepository.findByYearForUpdate((short) 2027)).thenReturn(Optional.of(newYearCounter));

        IssuedInvoice previousYearInvoice = service(Instant.parse("2026-12-31T16:59:59Z")).issueOrGet(44L);
        assertThat(previousYearInvoice.getInvoiceNumber()).isEqualTo("2026-000846");

        when(invoiceRepository.findByBookingId(44L)).thenReturn(Optional.empty());
        IssuedInvoice firstNewYearInvoice = service(Instant.parse("2026-12-31T17:00:00Z")).issueOrGet(44L);

        assertThat(firstNewYearInvoice.getInvoiceNumber()).isEqualTo("2027-000001");
        assertThat(firstNewYearInvoice.getIssuedAt()).isEqualTo(OffsetDateTime.parse("2027-01-01T00:00:00+07:00"));
        assertThat(previousYearCounter.getLastNumber()).isEqualTo(846);
        assertThat(newYearCounter.getLastNumber()).isEqualTo(1);
        verify(counterRepository).initializeYear((short) 2026);
        verify(counterRepository).initializeYear((short) 2027);
    }

    @Test void rejectsExhaustedSequenceWithoutGeneratingPdf() {
        counter.setLastNumber(999999);
        assertThatThrownBy(() -> service(Instant.parse("2026-10-15T00:00:00Z")).issueOrGet(44L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
        verify(pdfService, never()).generate(any());
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test void returnsExistingInvoiceWithOriginalNumberAndPdfWithoutConsumingCounter() {
        byte[] original = { 0, 1, 2, (byte) 255 };
        IssuedInvoice existing = new IssuedInvoice(); existing.setInvoiceNumber("2025-000033");
        existing.setStatus(IssuedInvoice.Status.CANCELLED);
        existing.setPdfBytes(original);
        when(invoiceRepository.findByBookingId(44L)).thenReturn(Optional.of(existing));
        assertThat(service(Instant.parse("2026-10-15T00:00:00Z")).issueOrGet(44L)).isSameAs(existing);
        assertThat(existing.getInvoiceNumber()).isEqualTo("2025-000033");
        assertThat(existing.getPdfBytes()).containsExactly(original);
        verify(counterRepository, never()).initializeYear(anyShort());
        verify(pdfService, never()).generate(any());
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test void downloadsExistingInvoiceWithoutRecheckingChangedBookingOrSettlement() {
        IssuedInvoice existing = new IssuedInvoice();
        existing.setInvoiceNumber("2025-000033");
        existing.setStatus(IssuedInvoice.Status.ISSUED);
        existing.setPdfBytes(new byte[] { 4, 5, 6 });
        when(invoiceRepository.findByBookingId(44L)).thenReturn(Optional.of(existing));

        assertThat(service(Instant.EPOCH).downloadOrIssue(44L)).isSameAs(existing);

        verify(invoiceDataService, never()).getInvoiceData(44L);
        verify(counterRepository, never()).initializeYear(anyShort());
        verify(pdfService, never()).generate(any());
    }

    @Test void downloadsCancelledInvoiceWithoutReissuingOrChangingIt() {
        IssuedInvoice existing = new IssuedInvoice();
        existing.setInvoiceNumber("2025-000033");
        existing.setStatus(IssuedInvoice.Status.CANCELLED);
        existing.setPdfBytes(new byte[] { 4, 5, 6 });
        existing.setCancellationReason("Original cancellation");
        when(invoiceRepository.findByBookingId(44L)).thenReturn(Optional.of(existing));

        assertThat(service(Instant.EPOCH).downloadOrIssue(44L)).isSameAs(existing);

        assertThat(existing.getStatus()).isEqualTo(IssuedInvoice.Status.CANCELLED);
        assertThat(existing.getPdfBytes()).containsExactly(4, 5, 6);
        assertThat(existing.getCancellationReason()).isEqualTo("Original cancellation");
        verify(invoiceDataService, never()).getInvoiceData(44L);
        verify(counterRepository, never()).initializeYear(anyShort());
    }

    @Test void downloadingPreviouslyIssuedBookingDoesNotAllocateAnotherNumber() {
        IssuedInvoice existing = new IssuedInvoice();
        existing.setInvoiceNumber("2026-000123");
        existing.setStatus(IssuedInvoice.Status.ISSUED);
        existing.setPdfBytes(pdf);
        when(invoiceRepository.findByBookingId(44L)).thenReturn(Optional.of(existing));

        assertThat(service(Instant.parse("2027-01-01T00:00:00Z")).downloadOrIssue(44L)).isSameAs(existing);

        verify(invoiceDataService, never()).getInvoiceData(44L);
        verify(counterRepository, never()).initializeYear(anyShort());
        verify(counterRepository, never()).findByYearForUpdate(anyShort());
        verify(pdfService, never()).generate(any());
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test void downloadWithoutInvoiceStillRejectsIneligibleBooking() {
        when(invoiceDataService.getInvoiceData(44L))
                .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "not settled"));
        assertThatThrownBy(() -> service(Instant.EPOCH).downloadOrIssue(44L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.CONFLICT);
        verify(invoiceDataService).getInvoiceData(44L);
        verify(counterRepository, never()).initializeYear(anyShort());
        verify(pdfService, never()).generate(any());
    }

    @Test void cancelsInvoiceAndRetainsIssuedNumberWithoutChangingCounter() {
        IssuedInvoice invoice = new IssuedInvoice();
        invoice.setInvoiceNumber("2026-000123");
        invoice.setStatus(IssuedInvoice.Status.ISSUED);
        when(invoiceRepository.findByBookingIdForUpdate(44L)).thenReturn(Optional.of(invoice));
        when(invoiceRepository.saveAndFlush(invoice)).thenReturn(invoice);

        IssuedInvoice cancelled = service(Instant.parse("2026-10-15T17:00:00Z"))
                .cancel(44L, new InvoiceCancellationRequest("Duplicate booking"));

        assertThat(cancelled.getInvoiceNumber()).isEqualTo("2026-000123");
        assertThat(cancelled.getStatus()).isEqualTo(IssuedInvoice.Status.CANCELLED);
        assertThat(cancelled.getCancelledAt()).isEqualTo(OffsetDateTime.parse("2026-10-16T00:00:00+07:00"));
        assertThat(cancelled.getCancelledByUser()).isSameAs(actor);
        assertThat(cancelled.getCancellationReason()).isEqualTo("Duplicate booking");
        verify(counterRepository, never()).initializeYear(anyShort());
        verify(counterRepository, never()).findByYearForUpdate(anyShort());
    }

    @Test void repeatedCancellationReturnsOriginalCancellationWithoutChangingNumberOrMetadata() {
        IssuedInvoice invoice = new IssuedInvoice();
        invoice.setInvoiceNumber("2026-000123");
        invoice.setStatus(IssuedInvoice.Status.CANCELLED);
        invoice.setCancelledAt(OffsetDateTime.parse("2026-10-15T10:00:00+07:00"));
        invoice.setCancelledByUser(actor);
        invoice.setCancellationReason("Original reason");
        when(invoiceRepository.findByBookingIdForUpdate(44L)).thenReturn(Optional.of(invoice));

        assertThat(service(Instant.parse("2026-10-16T00:00:00Z"))
                .cancel(44L, new InvoiceCancellationRequest("A different retry reason"))).isSameAs(invoice);
        assertThat(invoice.getInvoiceNumber()).isEqualTo("2026-000123");
        assertThat(invoice.getCancellationReason()).isEqualTo("Original reason");
        verify(invoiceRepository, never()).saveAndFlush(any());
        verify(userRepository, never()).findWithRoleById(anyLong());
        verify(counterRepository, never()).initializeYear(anyShort());
    }

    @Test void pdfFailureDoesNotSaveOrAdvanceCounterInUnitFlow() {
        when(pdfService.generate(any())).thenThrow(new IllegalStateException("PDF failed"));
        assertThatThrownBy(() -> service(Instant.parse("2026-10-15T00:00:00Z")).issueOrGet(44L))
                .isInstanceOf(IllegalStateException.class).hasMessage("PDF failed");
        verify(transactionManager).rollback(any(TransactionStatus.class));
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test void invoicePersistenceFailureRollsBackCounterIncrementInTransaction() {
        counter.setInvoiceYear((short) 2026);
        counter.setLastNumber(40);
        when(counterRepository.findByYearForUpdate((short) 2026)).thenReturn(Optional.of(counter));
        when(invoiceRepository.saveAndFlush(any())).thenThrow(new IllegalStateException("invoice insert failed"));

        assertThatThrownBy(() -> service(Instant.parse("2026-10-15T00:00:00Z")).issueOrGet(44L))
                .isInstanceOf(IllegalStateException.class).hasMessage("invoice insert failed");

        assertThat(counter.getLastNumber()).isEqualTo(41);
        verify(transactionManager).rollback(any(TransactionStatus.class));
        verify(transactionManager, never()).commit(any(TransactionStatus.class));
        verify(counterRepository).initializeYear((short) 2026);
        verify(counterRepository).findByYearForUpdate((short) 2026);
        verify(invoiceRepository).saveAndFlush(argThat(invoice -> invoice.getInvoiceNumber().equals("2026-000041")));
    }

    @Test void recognizesOnlyBookingUniqueConstraintAndReadsWinningInvoice() {
        IssuedInvoice winner = new IssuedInvoice(); winner.setInvoiceNumber("2026-000002");
        DataIntegrityViolationException bookingConflict = integrity("uq_issued_invoices_booking");
        when(invoiceRepository.saveAndFlush(any())).thenThrow(bookingConflict);
        when(invoiceRepository.findByBookingId(44L)).thenReturn(Optional.empty(), Optional.of(winner));
        assertThat(service(Instant.parse("2026-10-15T00:00:00Z")).issueOrGet(44L)).isSameAs(winner);

        reset(invoiceRepository);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenAnswer(invocation -> mock(TransactionStatus.class));
        when(bookingRepository.findByIdForUpdate(44L)).thenReturn(Optional.of(booking));
        when(invoiceRepository.findByBookingId(44L)).thenReturn(Optional.empty());
        when(userRepository.findWithRoleById(9L)).thenReturn(Optional.of(actor));
        when(invoiceDataService.getInvoiceData(44L)).thenReturn(data);
        when(counterRepository.findByYearForUpdate(any(Short.class))).thenReturn(Optional.of(counter));
        when(pdfService.generate(any())).thenReturn(pdf);
        when(objectMapper.convertValue(any(), any(tools.jackson.core.type.TypeReference.class))).thenReturn(Map.of());
        DataIntegrityViolationException otherConflict = integrity("uq_issued_invoices_number");
        when(invoiceRepository.saveAndFlush(any())).thenThrow(otherConflict);
        assertThatThrownBy(() -> service(Instant.parse("2026-10-15T00:00:00Z")).issueOrGet(44L))
                .isSameAs(otherConflict);
    }

    @Test void rejectsMissingJwtInvalidSubjectAndUnavailableOrInactiveIssuer() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service(Instant.EPOCH).issueOrGet(44L)).isInstanceOf(AccessDeniedException.class);
        setJwtSubject("bad");
        assertThatThrownBy(() -> service(Instant.EPOCH).issueOrGet(44L)).isInstanceOf(AccessDeniedException.class);
        setJwtSubject("9"); when(userRepository.findWithRoleById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service(Instant.EPOCH).issueOrGet(44L)).isInstanceOf(AccessDeniedException.class);
        when(actor.isActive()).thenReturn(false); when(userRepository.findWithRoleById(9L)).thenReturn(Optional.of(actor));
        assertThatThrownBy(() -> service(Instant.EPOCH).issueOrGet(44L)).isInstanceOf(AccessDeniedException.class);
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    @Test void missingBookingFailsWithoutIssuance() {
        when(bookingRepository.findByIdForUpdate(44L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service(Instant.EPOCH).issueOrGet(44L)).isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode").isEqualTo(HttpStatus.NOT_FOUND);
        verify(invoiceRepository, never()).saveAndFlush(any());
    }

    private static DataIntegrityViolationException integrity(String constraint) {
        return new DataIntegrityViolationException("constraint", new ConstraintViolationException("constraint", null, constraint));
    }
}
