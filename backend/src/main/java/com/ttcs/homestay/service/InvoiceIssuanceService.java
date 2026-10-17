package com.ttcs.homestay.service;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.ttcs.homestay.dto.InvoiceData;
import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.InvoiceNumberCounter;
import com.ttcs.homestay.entity.IssuedInvoice;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.InvoiceNumberCounterRepository;
import com.ttcs.homestay.repository.IssuedInvoiceRepository;
import com.ttcs.homestay.repository.UserRepository;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import com.ttcs.homestay.dto.InvoiceCancellationRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Issues an invoice and stores its immutable snapshot and original PDF atomically. */
@Service
public class InvoiceIssuanceService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final TypeReference<Map<String, Object>> SNAPSHOT_TYPE = new TypeReference<>() {};

    private final BookingRepository bookingRepository;
    private final IssuedInvoiceRepository invoiceRepository;
    private final InvoiceNumberCounterRepository counterRepository;
    private final UserRepository userRepository;
    private final InvoiceDataService invoiceDataService;
    private final InvoicePdfService pdfService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public InvoiceIssuanceService(BookingRepository bookingRepository, IssuedInvoiceRepository invoiceRepository,
            InvoiceNumberCounterRepository counterRepository, UserRepository userRepository,
            InvoiceDataService invoiceDataService, InvoicePdfService pdfService, ObjectMapper objectMapper,
            org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this(bookingRepository, invoiceRepository, counterRepository, userRepository, invoiceDataService,
                pdfService, objectMapper, Clock.systemUTC(), transactionManager);
    }

    InvoiceIssuanceService(BookingRepository bookingRepository, IssuedInvoiceRepository invoiceRepository,
            InvoiceNumberCounterRepository counterRepository, UserRepository userRepository,
            InvoiceDataService invoiceDataService, InvoicePdfService pdfService, ObjectMapper objectMapper,
            Clock clock, org.springframework.transaction.PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.invoiceRepository = invoiceRepository;
        this.counterRepository = counterRepository;
        this.userRepository = userRepository;
        this.invoiceDataService = invoiceDataService;
        this.pdfService = pdfService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public IssuedInvoice issueOrGet(long bookingId) {
        try {
            return transactionTemplate.execute(status -> issueInTransaction(bookingId));
        } catch (DataIntegrityViolationException conflict) {
            if (!isBookingInvoiceUniqueConflict(conflict)) throw conflict;
            return transactionTemplate.execute(status -> invoiceRepository.findByBookingId(bookingId)
                    .orElseThrow(() -> conflict));
        }
    }

    /** Reuses an existing invoice without revalidating issuance eligibility; only missing invoices are issued. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public IssuedInvoice downloadOrIssue(long bookingId) {
        try {
            return transactionTemplate.execute(status -> {
                Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                "Booking not found"));
                var existing = invoiceRepository.findByBookingId(bookingId);
                return existing.isPresent() ? existing.get() : issueLockedBooking(bookingId, booking);
            });
        } catch (DataIntegrityViolationException conflict) {
            if (!isBookingInvoiceUniqueConflict(conflict)) throw conflict;
            return transactionTemplate.execute(status -> invoiceRepository.findByBookingId(bookingId)
                    .orElseThrow(() -> conflict));
        }
    }

    @Transactional
    public IssuedInvoice cancel(long bookingId, InvoiceCancellationRequest request) {
        String reason = request.reason().trim();
        if (reason.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lý do hủy hóa đơn không được để trống.");
        }
        IssuedInvoice invoice = invoiceRepository.findByBookingIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy hóa đơn."));
        if (invoice.getStatus() == IssuedInvoice.Status.CANCELLED) return invoice;

        User actor = authenticatedUser();
        invoice.setStatus(IssuedInvoice.Status.CANCELLED);
        invoice.setCancelledAt(OffsetDateTime.now(clock.withZone(BUSINESS_ZONE)));
        invoice.setCancelledByUser(actor);
        invoice.setCancellationReason(reason);
        return invoiceRepository.saveAndFlush(invoice);
    }

    private IssuedInvoice issueInTransaction(long bookingId) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Không tìm thấy booking để lập hóa đơn."));
        var existing = invoiceRepository.findByBookingId(bookingId);
        if (existing.isPresent()) return existing.get();

        return issueLockedBooking(bookingId, booking);
    }

    private IssuedInvoice issueLockedBooking(long bookingId, Booking booking) {
        User actor = authenticatedUser();
        InvoiceData data = invoiceDataService.getInvoiceData(bookingId);
        OffsetDateTime issuedAt = OffsetDateTime.now(clock.withZone(BUSINESS_ZONE));
        int year = issuedAt.getYear();
        if (year < 1 || year > 9999) throw new IllegalStateException("Unsupported invoice year");
        counterRepository.initializeYear((short) year);
        InvoiceNumberCounter counter = counterRepository.findByYearForUpdate((short) year)
                .orElseThrow(() -> new IllegalStateException("Invoice counter initialization failed"));
        if (counter.getLastNumber() >= 999999) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đã hết số hóa đơn trong năm.");
        }
        int sequence = counter.getLastNumber() + 1;
        counter.setLastNumber(sequence);
        String invoiceNumber = String.format(java.util.Locale.ROOT, "%04d-%06d", year, sequence);
        LocalDate issuedDate = issuedAt.toLocalDate();
        InvoiceData issuedData = data.withIssuance(invoiceNumber, issuedDate);
        byte[] pdf = pdfService.generate(issuedData);

        IssuedInvoice invoice = new IssuedInvoice();
        invoice.setBooking(booking);
        invoice.setInvoiceYear((short) year);
        invoice.setSequenceNumber(sequence);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setIssuedAt(issuedAt);
        invoice.setIssuedByUser(actor);
        invoice.setIssuedByName(actor.getFullName());
        invoice.setSnapshotJson(objectMapper.convertValue(issuedData, SNAPSHOT_TYPE));
        invoice.setPdfBytes(pdf);
        invoice.setPdfSha256(sha256(pdf));
        invoice.setStatus(IssuedInvoice.Status.ISSUED);
        return invoiceRepository.saveAndFlush(invoice);
    }

    private User authenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw new AccessDeniedException("Authenticated user is required to issue an invoice.");
        }
        final long userId;
        try {
            userId = Long.parseLong(token.getToken().getSubject());
        } catch (NumberFormatException exception) {
            throw new AccessDeniedException("Authenticated user identity is invalid.");
        }
        return userRepository.findWithRoleById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new AccessDeniedException("Authenticated user is unavailable."));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static boolean isBookingInvoiceUniqueConflict(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && "uq_issued_invoices_booking".equalsIgnoreCase(violation.getConstraintName())) {
                return true;
            }
        }
        return false;
    }
}
