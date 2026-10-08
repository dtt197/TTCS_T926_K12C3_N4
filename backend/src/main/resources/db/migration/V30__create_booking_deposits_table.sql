-- Sprint 3 S3-01 Lát 1: tạo bảng ghi nhận tiền cọc
-- Mỗi booking chỉ có tối đa 1 khoản cọc ban đầu.
CREATE TABLE booking_deposits (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    amount NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    payment_method VARCHAR(20) NOT NULL,
    received_date DATE,
    payment_reference VARCHAR(255),
    reservation_code VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(120),

    CONSTRAINT fk_booking_deposits_booking
        FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,

    CONSTRAINT uc_booking_deposits_booking UNIQUE (booking_id),

    CONSTRAINT chk_booking_deposits_payment_method
        CHECK (payment_method IN ('CASH', 'BANK_TRANSFER'))
);

CREATE INDEX idx_booking_deposits_booking_id ON booking_deposits(booking_id);
