CREATE TABLE booking_guests (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings(id) ON DELETE CASCADE,
    guest_order INTEGER NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    identity_number VARCHAR(12),
    CONSTRAINT booking_guests_order_check CHECK (guest_order >= 0),
    CONSTRAINT booking_guests_identity_check CHECK (
        (guest_order = 0 AND identity_number ~ '^[0-9]{12}$')
        OR (guest_order > 0 AND identity_number IS NULL)
    ),
    CONSTRAINT uk_booking_guests_booking_order UNIQUE (booking_id, guest_order)
);
