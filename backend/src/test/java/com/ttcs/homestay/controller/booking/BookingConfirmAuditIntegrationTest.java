package com.ttcs.homestay.controller.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ttcs.homestay.entity.Booking;
import com.ttcs.homestay.entity.BookingStatus;
import com.ttcs.homestay.entity.User;
import com.ttcs.homestay.repository.BookingRepository;
import com.ttcs.homestay.repository.RoleRepository;
import com.ttcs.homestay.repository.UserRepository;
import com.ttcs.homestay.security.JwtTokenService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:booking_confirm_audit;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class BookingConfirmAuditIntegrationTest {
    @Test
    void depositEqualToDatabaseTotalSucceeds() throws Exception {
        confirm(booking.getId(), 500000).andExpect(status().isOk());
        assertThat(auditCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT amount FROM booking_deposits", java.math.BigDecimal.class))
                .isEqualByComparingTo("500000");
    }

    @Test
    void existingDepositOnPendingBookingIsRejectedWithoutAnotherPayment() throws Exception {
        jdbc.update("INSERT INTO booking_deposits (booking_id, amount, payment_method, created_at) VALUES (?, 100000, 'CASH', CURRENT_TIMESTAMP)", booking.getId());
        confirm(booking.getId(), 200000).andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value("Đã có tiền cọc cho booking này"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_deposits", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT amount FROM booking_deposits", java.math.BigDecimal.class))
                .isEqualByComparingTo("100000");
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(BookingStatus.CHO_XAC_NHAN);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getHoldExpiresAt()).isNotNull();
        assertThat(auditCount()).isZero();
    }

    @Test
    void depositAboveDatabaseTotalFailsWithoutChanges() throws Exception {
        confirm(booking.getId(), 500001).andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value("Tiền cọc không được lớn hơn tổng tiền đặt phòng"));
        assertUnchanged();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = BookingStatus.class, names = "CHO_XAC_NHAN",
            mode = org.junit.jupiter.params.provider.EnumSource.Mode.EXCLUDE)
    void rejectsEveryNonPendingStatusWithoutChanges(BookingStatus initialStatus) throws Exception {
        booking.setStatus(initialStatus);
        bookings.saveAndFlush(booking);
        confirm(booking.getId(), 200000).andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value("Booking không ở trạng thái chờ xác nhận, không thể xác nhận tiền cọc"));
        assertThat(bookings.findById(booking.getId()).orElseThrow().getStatus()).isEqualTo(initialStatus);
        assertThat(bookings.findById(booking.getId()).orElseThrow().getHoldExpiresAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_deposits", Integer.class)).isZero();
        assertThat(auditCount()).isZero();
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {true, false})
    void expiredPendingBookingCannotBeConfirmed(boolean explicitDeadline) throws Exception {
        booking.setCreatedAt(OffsetDateTime.now().minusHours(25));
        booking.setHoldExpiresAt(explicitDeadline ? OffsetDateTime.now().minusHours(1) : null);
        bookings.saveAndFlush(booking);
        var storedDeadline = bookings.findById(booking.getId()).orElseThrow().getHoldExpiresAt();
        confirm(booking.getId(), 200000).andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value("Đặt phòng đã hết hạn giữ chỗ, không thể xác nhận"));
        var saved = bookings.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CHO_XAC_NHAN);
        assertThat(saved.getHoldExpiresAt()).isEqualTo(storedDeadline);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_deposits", Integer.class)).isZero();
        assertThat(auditCount()).isZero();
    }

    @Test
    void concurrentRequestsCommitOnlyOneConfirmationAndAudit() throws Exception {
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var ready = new java.util.concurrent.CountDownLatch(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.Callable<Integer> request = () -> {
            ready.countDown();
            if (!start.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Start timeout");
            return confirm(booking.getId(), 200000).andReturn().getResponse().getStatus();
        };
        try {
            var first = executor.submit(request);
            var second = executor.submit(request);
            assertThat(ready.await(10, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(first.get(15, java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15, java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_deposits", Integer.class)).isEqualTo(1);
            assertThat(auditCount()).isEqualTo(1);
            var saved = bookings.findById(booking.getId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(BookingStatus.DA_XAC_NHAN);
            assertThat(saved.getHoldExpiresAt()).isNull();
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private BookingRepository bookings;
    @Autowired private UserRepository users;
    @Autowired private RoleRepository roles;
    @Autowired private JwtTokenService tokens;
    @Autowired private PlatformTransactionManager transactionManager;

    private Booking booking;
    private User actor;
    private String accessToken;

    @BeforeEach
    void setUp() {
        // H2 needs an alias for PostgreSQL's timestamp type; run the actual audit DDL.
        jdbc.execute("CREATE DOMAIN IF NOT EXISTS TIMESTAMPTZ AS TIMESTAMP WITH TIME ZONE");
        jdbc.execute("DROP TABLE IF EXISTS audit_logs");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V13__create_audit_logs.sql"))
                .execute(jdbc.getDataSource());
        jdbc.update("DELETE FROM booking_deposits");
        bookings.deleteAll();
        users.deleteAll();
        roles.deleteAll();
        jdbc.update("INSERT INTO roles (code, name) VALUES ('RECEPTIONIST', 'Receptionist')");
        actor = User.createStaff("Receptionist", "receptionist@homestay.local", null,
                roles.findByCode("RECEPTIONIST").orElseThrow(), true, "unused-test-hash");
        actor.changePassword("changed-test-hash");
        actor = users.saveAndFlush(actor);
        accessToken = tokens.issueTokens(actor).accessToken();

        booking = new Booking();
        booking.setBookingCode("BK-AUDIT-01");
        booking.setGuestName("Guest");
        booking.setGuestEmail("guest@example.com");
        booking.setStatus(BookingStatus.CHO_XAC_NHAN);
        booking.setRoomTypeNameSnapshot("Standard");
        booking.setCheckInDate(LocalDate.now().plusDays(2));
        booking.setCheckOutDate(LocalDate.now().plusDays(3));
        booking.setWeekendDaysSnapshot("FRIDAY,SATURDAY");
        booking.setTotalAmount(500000);
        booking.setCreatedAt(OffsetDateTime.now());
        booking.setHoldExpiresAt(OffsetDateTime.now().plusHours(24));
        booking = bookings.saveAndFlush(booking);
    }

    private ResultActions confirm(long id, int amount) throws Exception {
        return mvc.perform(put("/api/bookings/{id}/confirm", id)
                .header("Authorization", "Bearer " + accessToken)
                .with(request -> { request.setRemoteAddr("192.0.2.10"); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount":%d,"paymentMethod":"CASH","receivedDate":"2026-10-08",
                         "createdBy":"spoofed@example.com"}
                        """.formatted(amount)));
    }

    private int auditCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE action = 'BOOKING_CONFIRMED'",
                Integer.class);
    }

    private void assertUnchanged() {
        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.CHO_XAC_NHAN);
        assertThat(saved.getHoldExpiresAt()).isNotNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM booking_deposits", Integer.class)).isZero();
        assertThat(auditCount()).isZero();
    }

    @Test
    void commitsExactlyOneAuditWithJwtActorAndPreservesDepositAndHoldChanges() throws Exception {
        confirm(booking.getId(), 200000).andExpect(status().isOk());
        assertThat(auditCount()).isEqualTo(1);
        var log = jdbc.queryForMap("SELECT * FROM audit_logs WHERE action = 'BOOKING_CONFIRMED'");
        assertThat(log.get("actor_user_id")).isEqualTo(actor.getId());
        assertThat(log.get("actor_email")).isEqualTo(actor.getEmail());
        assertThat(log.get("account_email")).isEqualTo("Booking BK-AUDIT-01");
        assertThat(log.get("user_id")).isNull();
        assertThat(log.get("result")).isEqualTo("SUCCESS");
        assertThat(log.get("ip_address")).isEqualTo("192.0.2.10");
        assertThat(log.get("occurred_at")).isNotNull();
        Booking saved = bookings.findById(booking.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(BookingStatus.DA_XAC_NHAN);
        assertThat(saved.getHoldExpiresAt()).isNull();
        assertThat(jdbc.queryForObject("SELECT amount FROM booking_deposits WHERE booking_id = ?",
                java.math.BigDecimal.class, booking.getId())).isEqualByComparingTo("200000");
        confirm(booking.getId(), 200000).andExpect(status().isConflict());
        assertThat(auditCount()).isEqualTo(1);
    }

    @Test
    void invalidDepositDoesNotCreateSuccessAudit() throws Exception {
        confirm(booking.getId(), 0).andExpect(status().isBadRequest());
        assertUnchanged();
    }

    @Test
    void missingBookingDoesNotCreateSuccessAudit() throws Exception {
        confirm(Long.MAX_VALUE, 200000).andExpect(status().isNotFound());
        assertUnchanged();
    }

    @Test
    void auditInsertFailureRollsBackBookingAndDeposit() {
        jdbc.execute("ALTER TABLE audit_logs ADD CONSTRAINT reject_confirmation CHECK (action <> 'BOOKING_CONFIRMED')");
        assertThatThrownBy(() -> confirm(booking.getId(), 200000)).hasRootCauseInstanceOf(java.sql.SQLException.class);
        assertUnchanged();
    }

    @Test
    void rollbackAfterSuccessfulAuditInsertRemovesAllChanges() {
        new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
            try {
                confirm(booking.getId(), 200000).andExpect(status().isOk());
                assertThat(auditCount()).isEqualTo(1);
                transaction.setRollbackOnly();
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
        });
        assertUnchanged();
    }
}
