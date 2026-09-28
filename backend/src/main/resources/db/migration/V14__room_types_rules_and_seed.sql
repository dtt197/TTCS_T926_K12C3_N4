-- S1-06: ràng buộc dữ liệu loại phòng + 3 loại phòng mẫu khớp với 4 phòng demo ở V6.
-- Phòng (bảng rooms) gắn với loại phòng qua tên: rooms.room_type = room_types.name (không phân biệt hoa thường).

UPDATE room_types SET status = TRUE WHERE status IS NULL;
ALTER TABLE room_types ALTER COLUMN status SET DEFAULT TRUE;
ALTER TABLE room_types ALTER COLUMN status SET NOT NULL;

-- AC2: sức chứa tối đa không nhỏ hơn sức chứa tiêu chuẩn.
-- NOT VALID: chỉ kiểm tra dữ liệu mới, không làm hỏng máy nào lỡ có dữ liệu thử cũ.
ALTER TABLE room_types
    ADD CONSTRAINT room_types_capacity_check
        CHECK (standard_capacity > 0 AND max_capacity >= standard_capacity AND number_of_beds > 0)
        NOT VALID;

-- AC3: mã loại phòng duy nhất, không phân biệt hoa thường (DOI và doi coi là trùng).
CREATE UNIQUE INDEX IF NOT EXISTS room_types_code_lower_key ON room_types (LOWER(code));

INSERT INTO room_types (code, name, standard_capacity, max_capacity, number_of_beds, description, status)
VALUES
    ('DON',      'Phòng đơn',      1, 2, 1, 'Phòng cho 1 người, 1 giường đơn',   TRUE),
    ('DOI',      'Phòng đôi',      2, 3, 1, 'Phòng cho 2 người, 1 giường đôi',   TRUE),
    ('GIA_DINH', 'Phòng gia đình', 4, 5, 2, 'Phòng gia đình, 2 giường đôi',      TRUE)
ON CONFLICT DO NOTHING;