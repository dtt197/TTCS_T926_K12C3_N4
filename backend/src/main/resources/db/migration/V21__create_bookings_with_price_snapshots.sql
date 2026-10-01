CREATE TABLE bookings (
    id BIGSERIAL PRIMARY KEY,
    room_type_id INTEGER REFERENCES room_types(id) ON DELETE SET NULL,
    room_type_name_snapshot VARCHAR(100) NOT NULL,
    check_in_date DATE NOT NULL,
    check_out_date DATE NOT NULL,
    weekday_price_snapshot BIGINT NOT NULL,
    weekend_price_snapshot BIGINT NOT NULL,
    weekend_days_snapshot VARCHAR(100) NOT NULL,
    total_amount BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT bookings_dates_check CHECK (check_out_date > check_in_date),
    CONSTRAINT bookings_weekday_price_check CHECK (weekday_price_snapshot > 0),
    CONSTRAINT bookings_weekend_price_check CHECK (weekend_price_snapshot > 0),
    CONSTRAINT bookings_total_amount_check CHECK (total_amount > 0)
);