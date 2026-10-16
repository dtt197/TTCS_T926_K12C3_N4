package com.ttcs.homestay.controller.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.ttcs.homestay.dto.InvoiceData;
import com.ttcs.homestay.entity.BookingSettlement;
import com.ttcs.homestay.security.ApiSecurityPolicyCatalog;
import com.ttcs.homestay.entity.Role;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.service.InvoiceDataService;
import com.ttcs.homestay.service.InvoicePdfService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.dto.InvoiceData;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingInvoiceControllerTest {
    @Autowired private MockMvc securedMockMvc;
    @MockitoBean private InvoiceDataService securedDataService;
    @MockitoBean private InvoicePdfService securedPdfService;
    @MockitoBean private JwtDecoder jwtDecoder;
    @MockitoBean private UserRepository userRepository;

    @BeforeEach
    void setUpSecurityMocks() {
        mockActiveUser(301L, "RECEPTIONIST");
        mockActiveUser(302L, "OWNER");
        mockActiveUser(303L, "HOUSEKEEPING");
        mockActiveUser(304L, "ADMIN");
        when(jwtDecoder.decode("receptionist-token")).thenReturn(jwt("301", "RECEPTIONIST"));
        when(jwtDecoder.decode("owner-token")).thenReturn(jwt("302", "OWNER"));
        when(jwtDecoder.decode("housekeeping-token")).thenReturn(jwt("303", "HOUSEKEEPING"));
        when(jwtDecoder.decode("admin-token")).thenReturn(jwt("304", "ADMIN"));
        when(securedDataService.getInvoiceData(44L)).thenReturn(invoice("BK-44"));
        when(securedPdfService.generate(org.mockito.ArgumentMatchers.any())).thenReturn(new byte[] {1, 2, 3});
    }

    private void mockActiveUser(long userId, String roleCode) {
        Role role = mock(Role.class);
        when(role.getCode()).thenReturn(roleCode);
        User user = mock(User.class);
        when(user.isActive()).thenReturn(true);
        when(user.isMustChangePassword()).thenReturn(false);
        when(user.getRole()).thenReturn(role);
        when(user.getTokenVersion()).thenReturn(0);
        when(userRepository.findWithRoleById(userId)).thenReturn(Optional.of(user));
    }

    private Jwt jwt(String userId, String role) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("jwt-" + userId).header("alg", "HS256")
                .issuer("homestay").subject(userId).audience(List.of("homestay-api"))
                .issuedAt(now).expiresAt(now.plusSeconds(3600)).claim("typ", "access")
                .claim("role", role).claim("tv", 0).claim("email", userId + "@example.test")
                .claim("fullName", userId + " Test Actor").build();
    }

    private MockHttpServletRequestBuilder securedRequest(String token) {
        var request = get("/api/bookings/44/invoice.pdf");
        return token == null ? request : request.header("Authorization", "Bearer " + token);
    }

    @Test
    void receptionistCanDownloadInvoiceThroughSecurityFilters() throws Exception {
        securedMockMvc.perform(securedRequest("receptionist-token")).andExpect(status().isOk());
    }

    @Test
    void ownerCanDownloadInvoiceThroughSecurityFilters() throws Exception {
        securedMockMvc.perform(securedRequest("owner-token")).andExpect(status().isOk());
    }

    @Test
    void housekeepingIsForbiddenFromDownloadingInvoice() throws Exception {
        securedMockMvc.perform(securedRequest("housekeeping-token")).andExpect(status().isForbidden());
    }

    @Test
    void adminIsForbiddenFromDownloadingInvoice() throws Exception {
        securedMockMvc.perform(securedRequest("admin-token")).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedRequestIsUnauthorized() throws Exception {
        securedMockMvc.perform(securedRequest(null)).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsPdfMimeTypeAndSanitizedAttachmentFilename() throws Exception {
        InvoiceDataService dataService = org.mockito.Mockito.mock(InvoiceDataService.class);
        InvoicePdfService pdfService = org.mockito.Mockito.mock(InvoicePdfService.class);
        when(dataService.getInvoiceData(44L)).thenReturn(invoice("BK/44\r\nX"));
        when(pdfService.generate(org.mockito.ArgumentMatchers.any())).thenReturn(new byte[] {1, 2, 3});
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new BookingInvoiceController(dataService, pdfService)).build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/bookings/44/invoice.pdf"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType(MediaType.APPLICATION_PDF))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                        .string("Content-Disposition", org.hamcrest.Matchers.containsString("invoice_BK_44__X.pdf")));
    }

    @Test
    void returnsControlledInvoiceReasonInsteadOfGenericConflictBody() throws Exception {
        when(securedDataService.getInvoiceData(44L)).thenThrow(new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT, "Thiếu địa chỉ homestay trong cấu hình."));

        securedMockMvc.perform(securedRequest("receptionist-token"))
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value("Thiếu địa chỉ homestay trong cấu hình."));
    }

    @Test
    void onlyReceptionistAndOwnerHaveCatalogAccess() {
        ApiSecurityPolicyCatalog catalog = new ApiSecurityPolicyCatalog();
        assertThat(catalog.findPolicy("GET", "/api/bookings/44/invoice.pdf").roles())
                .containsExactly("RECEPTIONIST", "OWNER");
    }

    private static InvoiceData invoice(String code) {
        return new InvoiceData(44L, code, "Nguyễn An", null, null, null,
                new InvoiceData.Homestay("Nhà nghỉ", "Hà Nội", "090", null, null),
                new InvoiceData.Room(1L, "201", "Phòng đơn"), LocalDate.of(2026, 10, 10),
                LocalDate.of(2026, 10, 11), 1,
                List.of(new InvoiceData.LineItem("ROOM", "Tiền phòng", BigDecimal.TEN)),
                BigDecimal.TEN, BigDecimal.ZERO, BookingSettlement.SettlementType.COLLECT,
                BigDecimal.TEN, BigDecimal.TEN, null, false);
    }
}
