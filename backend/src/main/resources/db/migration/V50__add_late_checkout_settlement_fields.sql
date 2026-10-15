ALTER TABLE booking_settlements
    ADD COLUMN late_checkout_fee BIGINT NOT NULL DEFAULT 0 CHECK (late_checkout_fee >= 0),
    ADD COLUMN additional_surcharge_total BIGINT NOT NULL DEFAULT 0 CHECK (additional_surcharge_total >= 0),
    ADD COLUMN final_total BIGINT CHECK (final_total IS NULL OR final_total >= 0),
    ADD COLUMN late_checkout_hours INTEGER NOT NULL DEFAULT 0 CHECK (late_checkout_hours >= 0),
    ADD COLUMN late_checkout_fee_per_hour BIGINT CHECK (late_checkout_fee_per_hour >= 0),
    ADD COLUMN checkout_time_snapshot TIME,
    ADD COLUMN operating_settings_id BIGINT,
    ADD COLUMN late_checkout_pricing_basis VARCHAR(20);

ALTER TABLE booking_settlements
    ADD CONSTRAINT chk_late_checkout_basis
    CHECK (late_checkout_pricing_basis IS NULL OR late_checkout_pricing_basis IN ('NONE', 'HOURLY', 'LAST_NIGHT'));
