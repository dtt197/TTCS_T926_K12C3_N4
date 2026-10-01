-- S2-07 Lát 1: khách tự đặt phòng trên trang công khai.
-- Các cột để trống được với booking do nhân viên tạo (S2-01) trước đây.
ALTER TABLE bookings
    ADD COLUMN guest_phone VARCHAR(20),
    ADD COLUMN guest_email VARCHAR(150),
    ADD COLUMN guest_count INTEGER,
    ADD COLUMN note VARCHAR(500),
    ADD COLUMN hold_expires_at TIMESTAMPTZ;

ALTER TABLE bookings
    ADD CONSTRAINT bookings_guest_count_check CHECK (guest_count IS NULL OR guest_count >= 1);

-- Tìm booking trùng ngày của một loại phòng khi kiểm tra phòng trống.
CREATE INDEX idx_bookings_room_type_dates ON bookings (room_type_id, check_in_date, check_out_date);