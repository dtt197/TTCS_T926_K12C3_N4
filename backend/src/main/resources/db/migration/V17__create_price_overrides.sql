-- S2-02: giá đè theo mùa hoặc ngày lễ cho từng loại phòng.
-- Khoảng ngày áp dụng tính theo ĐÊM, gồm cả hai đầu:
-- đợt 29/04 → 01/05 áp cho các đêm 29/04, 30/04 và 01/05.
CREATE TABLE price_overrides (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    room_type_id INTEGER NOT NULL REFERENCES room_types(id) ON DELETE CASCADE,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    price_per_night BIGINT NOT NULL,
    created_by_name VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT price_overrides_dates_check CHECK (end_date >= start_date),
    CONSTRAINT price_overrides_price_check CHECK (price_per_night > 0)
);

-- Lát 2 (chặn trùng ngày) và tính giá từng đêm sẽ tra theo loại phòng + khoảng ngày.
CREATE INDEX idx_price_overrides_room_type_dates
    ON price_overrides (room_type_id, start_date, end_date);