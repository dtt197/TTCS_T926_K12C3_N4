package com.ttcs.homestay.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tự động đảm bảo 4 tài khoản mẫu thử nghiệm cho 4 vai trò luôn tồn tại và sẵn sàng
 * sử dụng với mật khẩu chuẩn "password" khi ứng dụng khởi động (S4-09 / SCRUM-139).
 */
@Component
@Order(100)
public class DemoAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoAccountInitializer.class);
    private final JdbcTemplate jdbcTemplate;

    // Hash BCrypt chuẩn xác của mật khẩu "password"
    private static final String DEMO_PASSWORD_HASH = "$2a$10$86R0Ma3Tt357LyDMkPMisO8KNPrFR5XpR974IaxOXAF8HPGjFrfWq";

    public DemoAccountInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureDemoAccount("Chủ homestay Demo", "owner.demo@homestay.local", "OWNER");
            ensureDemoAccount("Lễ tân Demo", "letan.demo@homestay.local", "RECEPTIONIST");
            ensureDemoAccount("Buồng phòng Demo", "buongphong.demo@homestay.local", "HOUSEKEEPING");
            ensureDemoAccount("Demo Administrator", "admin.demo@homestay.local", "ADMIN");
            log.info("Initialized role-based demo accounts successfully (password: 'password')");
        } catch (Exception e) {
            log.warn("Skipping demo accounts initialization: {}", e.getMessage());
        }
    }

    private void ensureDemoAccount(String fullName, String email, String roleCode) {
        List<Long> roleIds = jdbcTemplate.queryForList("SELECT id FROM roles WHERE code = ?", Long.class, roleCode);
        if (roleIds.isEmpty()) {
            return;
        }
        Long roleId = roleIds.get(0);

        List<Integer> existing = jdbcTemplate.queryForList(
                "SELECT 1 FROM users WHERE LOWER(email) = LOWER(?)", Integer.class, email);

        if (existing.isEmpty()) {
            jdbcTemplate.update("""
                INSERT INTO users (
                    full_name, email, password_hash, role_id, is_active,
                    failed_login_count, token_version, must_change_password, locked_until, created_at, updated_at)
                VALUES (?, ?, ?, ?, TRUE, 0, 1, FALSE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, fullName, email, DEMO_PASSWORD_HASH, roleId);
        } else {
            jdbcTemplate.update("""
                UPDATE users
                SET password_hash = ?, role_id = ?, is_active = TRUE,
                    failed_login_count = 0, locked_until = NULL, must_change_password = FALSE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE LOWER(email) = LOWER(?)
            """, DEMO_PASSWORD_HASH, roleId, email);
        }
    }
}
