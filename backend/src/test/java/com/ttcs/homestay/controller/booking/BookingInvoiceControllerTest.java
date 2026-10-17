
package com.ttcs.homestay.controller.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingSettlement;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.IssuedInvoice;
import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.exception.BookingInvoiceExceptionHandler;
import com.ttcs.homestay.security.ApiSecurityPolicyCatalog;
import com.ttcs.homestay.service.InvoiceIssuanceService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
class BookingInvoiceControllerTest {

    @Autowired
    private MockMvc securedMockMvc;

    @MockitoBean
    private InvoiceIssuanceService securedIssuanceService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private com.ttcs.homestay.repository.UserRepository userRepository;

    private final byte[] pdfBytes = new byte[] { 1, 2, 3 };

    @BeforeEach
    void setUpSecurityMocks() {
        mockActiveUser(301L, "RECEPTIONIST");
        mockActiveUser(302L, "OWNER");
        mockActiveUser(303L, "HOUSEKEEPING");
        mockActiveUser(304L, "ADMIN");

        when(jwtDecoder.decode("receptionist-token"))
                .thenReturn(jwt("301", "RECEPTIONIST"));
        when(jwtDecoder.decode("owner-token"))
                .thenReturn(jwt("302", "OWNER"));
        when(jwtDecoder.decode("housekeeping-token"))
                .thenReturn(jwt("303", "HOUSEKEEPING"));
        when(jwtDecoder.decode("admin-token"))
                .thenReturn(jwt("304", "ADMIN"));

        IssuedInvoice invoice = issuedInvoice("2026-000044", pdfBytes);
        when(securedIssuanceService.downloadOrIssue(44L)).thenReturn(invoice);
    }

    private void mockActiveUser(long userId, String roleCode) {
        Role role = mock(Role.class);
        when(role.getCode()).thenReturn(roleCode);

        User user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(user.isMustChangePassword()).thenReturn(false);
        when(user.getRole()).thenReturn(role);
        when(user.getTokenVersion()).thenReturn(0);

        when(userRepository.findWithRoleById(userId))
                .thenReturn(Optional.of(user));
    }

    private Jwt jwt(String userId, String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("jwt-" + userId)
                .header("alg", "HS256")
                .issuer("homestay")
                .subject(userId)
                .audience(List.of("homestay-api"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("typ", "access")
                .claim("role", role)
                .claim("tv", 0)
                .claim("email", userId + "@example.test")
                .claim("fullName", userId + " Test Actor")
                .build();
    }

    private MockHttpServletRequestBuilder securedRequest(String token) {
        var request = get("/api/bookings/44/invoice.pdf");
        return token == null
                ? request
                : request.header("Authorization", "Bearer " + token);
    }

    private MockHttpServletRequestBuilder securedCancelRequest(String token) {
        return put("/api/bookings/44/invoice/cancel")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Duplicate booking\"}");
    }

    private IssuedInvoice issuedInvoice(String invoiceNumber, byte[] bytes) {
        IssuedInvoice invoice = mock(IssuedInvoice.class);
        when(invoice.getInvoiceNumber()).thenReturn(invoiceNumber);
        when(invoice.getPdfBytes()).thenReturn(bytes);
        return invoice;
    }

    @Test
    void receptionistCanDownloadInvoiceThroughSecurityFilters() throws Exception {
        securedMockMvc.perform(securedRequest("receptionist-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(content().bytes(pdfBytes));

        verify(securedIssuanceService).downloadOrIssue(44L);
    }

    @Test
    void ownerCanDownloadInvoiceThroughSecurityFilters() throws Exception {
        securedMockMvc.perform(securedRequest("owner-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF));
    }

    @Test
    void housekeepingIsForbiddenFromDownloadingInvoice() throws Exception {
        securedMockMvc.perform(securedRequest("housekeeping-token"))
                .andExpect(status().isForbidden());

        verify(securedIssuanceService, never()).downloadOrIssue(44L);
    }

    @Test
    void adminIsForbiddenFromDownloadingInvoice() throws Exception {
        securedMockMvc.perform(securedRequest("admin-token"))
                .andExpect(status().isForbidden());

        verify(securedIssuanceService, never()).downloadOrIssue(44L);
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        securedMockMvc.perform(securedRequest(null))
                .andExpect(status().isUnauthorized());

        verify(securedIssuanceService, never()).downloadOrIssue(44L);
    }

    @Test
    void returnsPdfMimeTypeAndInvoiceNumberFilename() throws Exception {
        byte[] expectedPdf = new byte[] { 9, 8, 7 };
        InvoiceIssuanceService issuanceService = mock(InvoiceIssuanceService.class);

        IssuedInvoice invoice = issuedInvoice("2026-000123", expectedPdf);
        when(issuanceService.downloadOrIssue(44L)).thenReturn(invoice);

        MockMvc mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .standaloneSetup(new BookingInvoiceController(issuanceService))
                .build();

        mvc.perform(get("/api/bookings/44/invoice.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.containsString(
                                "invoice_2026-000123.pdf")))
                .andExpect(content().bytes(expectedPdf));

        verify(issuanceService).downloadOrIssue(44L);
    }

    @Test
    void returnsControlledInvoiceReasonInsteadOfGenericConflictBody() throws Exception {
        when(securedIssuanceService.downloadOrIssue(44L))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "Thiáº¿u Ä‘á»‹a chá»‰ homestay trong cáº¥u hÃ¬nh."));

        securedMockMvc.perform(securedRequest("receptionist-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Thiáº¿u Ä‘á»‹a chá»‰ homestay trong cáº¥u hÃ¬nh."));
    }

    @Test
    void rejectsInvoiceWhenBookingIsNotEligible() throws Exception {
        when(securedIssuanceService.downloadOrIssue(44L))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.CONFLICT,
                        "Booking chÆ°a tráº£ phÃ²ng nÃªn chÆ°a xuáº¥t Ä‘Æ°á»£c hoÃ¡ Ä‘Æ¡n"));

        securedMockMvc.perform(securedRequest("receptionist-token"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value("Booking chÆ°a tráº£ phÃ²ng nÃªn chÆ°a xuáº¥t Ä‘Æ°á»£c hoÃ¡ Ä‘Æ¡n"));
    }

    @Test
    void returnsNotFoundWhenBookingDoesNotExist() throws Exception {
        when(securedIssuanceService.downloadOrIssue(44L))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "KhÃ´ng tÃ¬m tháº¥y booking Ä‘á»ƒ láº­p hoÃ¡ Ä‘Æ¡n."));

        securedMockMvc.perform(securedRequest("receptionist-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message")
                        .value("KhÃ´ng tÃ¬m tháº¥y booking Ä‘á»ƒ láº­p hoÃ¡ Ä‘Æ¡n."));
    }

    @Test
    void onlyReceptionistAndOwnerHaveCatalogAccess() {
        ApiSecurityPolicyCatalog catalog = new ApiSecurityPolicyCatalog();

        assertThat(catalog.findPolicy("GET", "/api/bookings/44/invoice.pdf").roles())
                .containsExactly("RECEPTIONIST", "OWNER");
        assertThat(catalog.findPolicy("PUT", "/api/bookings/44/invoice/cancel").roles())
                .containsExactly("RECEPTIONIST", "OWNER");
    }

    @Test
    void receptionistCanCancelInvoiceAndResponsePreservesItsNumber() throws Exception {
        IssuedInvoice cancelled = new IssuedInvoice();
        cancelled.setInvoiceNumber("2026-000123");
        cancelled.setStatus(IssuedInvoice.Status.CANCELLED);
        cancelled.setCancelledAt(java.time.OffsetDateTime.parse("2026-10-16T00:00:00+07:00"));
        cancelled.setCancellationReason("Duplicate booking");
        when(securedIssuanceService.cancel(44L,
                new com.ttcs.homestay.dto.InvoiceCancellationRequest("Duplicate booking")))
                .thenReturn(cancelled);

        securedMockMvc.perform(securedCancelRequest("receptionist-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNumber").value("2026-000123"))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancellationReason").value("Duplicate booking"));
    }

    @Test
    void ownerCanCancelInvoiceThroughSecurityFilters() throws Exception {
        when(securedIssuanceService.cancel(44L,
                new com.ttcs.homestay.dto.InvoiceCancellationRequest("Duplicate booking")))
                .thenReturn(cancelledInvoice());

        securedMockMvc.perform(securedCancelRequest("owner-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        verify(securedIssuanceService).cancel(44L,
                new com.ttcs.homestay.dto.InvoiceCancellationRequest("Duplicate booking"));
    }

    @Test
    void housekeepingIsForbiddenFromCancellingInvoiceThroughSecurityFilters() throws Exception {
        securedMockMvc.perform(securedCancelRequest("housekeeping-token"))
                .andExpect(status().isForbidden());

        verify(securedIssuanceService, never()).cancel(org.mockito.ArgumentMatchers.eq(44L),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void adminIsForbiddenFromCancellingInvoiceThroughSecurityFilters() throws Exception {
        securedMockMvc.perform(securedCancelRequest("admin-token"))
                .andExpect(status().isForbidden());

        verify(securedIssuanceService, never()).cancel(org.mockito.ArgumentMatchers.eq(44L),
                org.mockito.ArgumentMatchers.any());
    }

    private IssuedInvoice cancelledInvoice() {
        IssuedInvoice invoice = new IssuedInvoice();
        invoice.setInvoiceNumber("2026-000123");
        invoice.setStatus(IssuedInvoice.Status.CANCELLED);
        invoice.setCancelledAt(java.time.OffsetDateTime.parse("2026-10-16T00:00:00+07:00"));
        invoice.setCancellationReason("Duplicate booking");
        return invoice;
    }
}
