CREATE TABLE booking_nightly_price_snapshots (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id),
    night_date DATE NOT NULL,
    price_type VARCHAR(20) NOT NULL,
    price_label VARCHAR(100) NOT NULL,
    room_price BIGINT NOT NULL CHECK (room_price >= 0),
    surcharge_amount BIGINT NOT NULL DEFAULT 0 CHECK (surcharge_amount >= 0),
    CONSTRAINT uq_booking_nightly_price_date UNIQUE (booking_id, night_date)
);

CREATE TABLE booking_settlements (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL UNIQUE REFERENCES bookings(id),
    room_id BIGINT NOT NULL REFERENCES rooms(id),
    room_total NUMERIC(19, 4) NOT NULL CHECK (room_total >= 0),
    deposit_total NUMERIC(19, 4) NOT NULL CHECK (deposit_total >= 0),
    settlement_type VARCHAR(10) NOT NULL CHECK (settlement_type IN ('COLLECT', 'ZERO', 'REFUND')),
    amount NUMERIC(19, 4) NOT NULL CHECK (amount >= 0),
    payment_method VARCHAR(20) CHECK (payment_method IN ('CASH', 'BANK_TRANSFER')),
    payment_reference VARCHAR(255),
    transaction_status VARCHAR(20) NOT NULL CHECK (transaction_status IN ('CONFIRMED', 'NOT_APPLICABLE')),
    acted_by VARCHAR(120) NOT NULL,
    settled_at TIMESTAMPTZ NOT NULL,
    checked_out_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_settlement_zero CHECK ((settlement_type = 'ZERO' AND amount = 0 AND payment_method IS NULL)
        OR (settlement_type <> 'ZERO' AND amount > 0 AND payment_method IS NOT NULL)),
    CONSTRAINT chk_settlement_confirmation CHECK (
        (settlement_type = 'ZERO' AND transaction_status = 'NOT_APPLICABLE')
        OR (settlement_type <> 'ZERO' AND transaction_status = 'CONFIRMED')),
    CONSTRAINT chk_settlement_bank_transfer_reference CHECK (
        payment_method <> 'BANK_TRANSFER' OR NULLIF(BTRIM(payment_reference), '') IS NOT NULL)
);

CREATE INDEX idx_booking_nightly_snapshots_booking ON booking_nightly_price_snapshots (booking_id, night_date);
