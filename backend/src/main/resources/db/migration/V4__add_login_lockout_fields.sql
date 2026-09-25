ALTER TABLE users
    ADD COLUMN failed_login_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN first_failed_login_at TIMESTAMP NULL,
    ADD COLUMN locked_until TIMESTAMP NULL;

ALTER TABLE users
    ADD CONSTRAINT chk_users_failed_login_count_non_negative
    CHECK (failed_login_count >= 0);