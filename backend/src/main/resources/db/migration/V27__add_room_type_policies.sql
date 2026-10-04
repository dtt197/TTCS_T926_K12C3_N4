-- S2-04: Bổ sung các chính sách nhận phòng, trả phòng, chính sách trẻ nhỏ, phụ thu và hủy phòng
ALTER TABLE room_types
    ADD COLUMN IF NOT EXISTS check_in_time TIME,
    ADD COLUMN IF NOT EXISTS check_out_time TIME,
    ADD COLUMN IF NOT EXISTS allow_children BOOLEAN DEFAULT TRUE,
    ADD COLUMN IF NOT EXISTS child_policy VARCHAR(500),
    ADD COLUMN IF NOT EXISTS extra_person_fee BIGINT,
    ADD COLUMN IF NOT EXISTS cancellation_policy VARCHAR(500);

-- Bảng mốc phí hủy phòng riêng cho từng loại phòng (nếu loại phòng có chính sách hủy riêng biệt)
CREATE TABLE IF NOT EXISTS room_type_cancellation_tiers (
    id BIGSERIAL PRIMARY KEY,
    room_type_id BIGINT NOT NULL REFERENCES room_types(id) ON DELETE CASCADE,
    hours_before_check_in INTEGER NOT NULL,
    refund_percent INTEGER NOT NULL,
    CONSTRAINT rt_cancellation_tiers_hours_check CHECK (hours_before_check_in >= 0),
    CONSTRAINT rt_cancellation_tiers_percent_check CHECK (refund_percent BETWEEN 0 AND 100),
    CONSTRAINT rt_cancellation_tiers_unique_hours UNIQUE (room_type_id, hours_before_check_in)
);
