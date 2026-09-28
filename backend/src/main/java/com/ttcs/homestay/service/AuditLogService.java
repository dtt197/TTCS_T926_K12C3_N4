package com.ttcs.homestay.service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ttcs.homestay.dto.audit.AuditLogEntry;
import com.ttcs.homestay.dto.audit.AuditLogPage;

@Service
public class AuditLogService {

    private static final ZoneId HO_CHI_MINH = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int PAGE_SIZE = 50;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public AuditLogService(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLogin(Long actorUserId, String actorEmail, Long userId,
            String accountEmail, String result, String ipAddress) {
        insert(actorUserId, actorEmail, userId, accountEmail, "LOGIN", result, ipAddress);
    }

    @Transactional
    public void recordSensitiveAction(Long actorUserId, String actorEmail, Long userId,
            String accountEmail, String action, String ipAddress) {
        insert(actorUserId, actorEmail, userId, accountEmail, action, "SUCCESS", ipAddress);
    }

    @Transactional(readOnly = true)
    public AuditLogPage findLogs(LocalDate from, LocalDate to, String account, int requestedPage) {
        int page = Math.max(0, requestedPage);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("pageSize", PAGE_SIZE)
                .addValue("offset", (long) page * PAGE_SIZE);
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");

        if (from != null) {
            where.append(" AND occurred_at >= :fromTime");
            parameters.addValue("fromTime", Timestamp.from(from.atStartOfDay(HO_CHI_MINH).toInstant()));
        }
        if (to != null) {
            where.append(" AND occurred_at < :toTime");
            parameters.addValue("toTime", Timestamp.from(to.plusDays(1).atStartOfDay(HO_CHI_MINH).toInstant()));
        }
        if (account != null && !account.isBlank()) {
            where.append(" AND (LOWER(account_email) LIKE :account OR LOWER(actor_email) LIKE :account)");
            parameters.addValue("account", "%" + account.trim().toLowerCase(Locale.ROOT) + "%");
        }

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs" + where,
                parameters,
                Long.class);
        List<AuditLogEntry> entries = jdbcTemplate.query(
                "SELECT id, occurred_at, actor_email, account_email, action, result, ip_address "
                        + "FROM audit_logs" + where + " ORDER BY occurred_at DESC, id DESC "
                        + "LIMIT :pageSize OFFSET :offset",
                parameters,
                (row, index) -> new AuditLogEntry(
                        row.getLong("id"),
                        row.getTimestamp("occurred_at").toInstant().atZone(HO_CHI_MINH).toOffsetDateTime(),
                        row.getString("actor_email"),
                        row.getString("account_email"),
                        row.getString("action"),
                        row.getString("result"),
                        row.getString("ip_address")));
        long totalElements = total == null ? 0 : total;
        int totalPages = (int) Math.ceil((double) totalElements / PAGE_SIZE);
        return new AuditLogPage(entries, page, PAGE_SIZE, totalElements, totalPages);
    }

    private void insert(Long actorUserId, String actorEmail, Long userId,
            String accountEmail, String action, String result, String ipAddress) {
        jdbcTemplate.update(
                "INSERT INTO audit_logs (actor_user_id, actor_email, user_id, account_email, action, result, ip_address) "
                        + "VALUES (:actorUserId, :actorEmail, :userId, :accountEmail, :action, :result, :ipAddress)",
                new MapSqlParameterSource()
                        .addValue("actorUserId", actorUserId)
                        .addValue("actorEmail", actorEmail)
                        .addValue("userId", userId)
                        .addValue("accountEmail", accountEmail)
                        .addValue("action", action)
                        .addValue("result", result)
                        .addValue("ipAddress", ipAddress));
    }
}