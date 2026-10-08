-- S3-01 Lát 4: append-only ledger entries for changes to the original booking deposit.
CREATE TABLE booking_deposit_adjustments (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    adjustment_type VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    reason VARCHAR(500) NOT NULL CHECK (char_length(btrim(reason)) BETWEEN 1 AND 500),
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_booking_deposit_adjustments_booking
        FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,

    CONSTRAINT chk_booking_deposit_adjustments_type
        CHECK (adjustment_type IN ('TANG', 'GIAM'))
);

CREATE INDEX idx_booking_deposit_adjustments_booking_created
    ON booking_deposit_adjustments (booking_id, created_at, id);
