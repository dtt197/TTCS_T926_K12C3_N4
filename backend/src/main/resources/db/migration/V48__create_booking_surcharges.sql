-- S4-02 Lát 1 (V48): tham số đơn giá thêm giường và các dòng phụ thu gắn với booking.
ALTER TABLE operating_settings
    ADD COLUMN extra_bed_fee BIGINT NOT NULL DEFAULT 0
        CONSTRAINT chk_operating_settings_extra_bed_fee CHECK (extra_bed_fee >= 0);

CREATE TABLE booking_surcharges (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    surcharge_type VARCHAR(20) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity >= 1),
    unit_price BIGINT NOT NULL CHECK (unit_price >= 0),
    nights INTEGER NOT NULL CHECK (nights >= 1),
    amount BIGINT NOT NULL,
    note VARCHAR(500),
    created_by VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_booking_surcharges_booking
        FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,

    CONSTRAINT chk_booking_surcharges_type
        CHECK (surcharge_type IN ('THEM_NGUOI', 'THEM_GIUONG')),

    CONSTRAINT chk_booking_surcharges_amount
        CHECK (amount = quantity * unit_price * nights)
);

CREATE INDEX idx_booking_surcharges_booking_created
    ON booking_surcharges (booking_id, created_at, id);