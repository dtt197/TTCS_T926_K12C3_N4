ALTER TABLE bookings
    ADD COLUMN booking_code VARCHAR(40),
    ADD COLUMN guest_name VARCHAR(120),
    ADD COLUMN status VARCHAR(30) NOT NULL DEFAULT 'CHO_XAC_NHAN';

-- Dữ liệu booking cũ nếu đã tồn tại
UPDATE bookings
SET booking_code = 'BK-' || LPAD(id::text, 6, '0')
WHERE booking_code IS NULL;

UPDATE bookings
SET guest_name = 'Khách chưa cập nhật'
WHERE guest_name IS NULL;

ALTER TABLE bookings
    ALTER COLUMN booking_code SET NOT NULL,
    ALTER COLUMN guest_name SET NOT NULL;

ALTER TABLE bookings
    ADD CONSTRAINT bookings_booking_code_unique UNIQUE (booking_code);

ALTER TABLE bookings
    ADD CONSTRAINT bookings_status_check
    CHECK (
        status IN (
            'CHO_XAC_NHAN',
            'DA_XAC_NHAN',
            'DA_HUY',
            'DA_NHAN_PHONG',
            'DA_TRA_PHONG'
        )
    );

CREATE INDEX idx_bookings_created_at_id
    ON bookings (created_at DESC, id DESC);