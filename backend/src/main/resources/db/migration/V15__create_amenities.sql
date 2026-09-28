-- S1-08: danh mục tiện nghi và tiện nghi gắn cho từng loại phòng.
CREATE TABLE amenities (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(100) NOT NULL,
    icon VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

-- AC1: mã tiện nghi duy nhất, không phân biệt hoa thường.
CREATE UNIQUE INDEX amenities_code_lower_key ON amenities (LOWER(code));

-- AC2: một loại phòng gắn nhiều tiện nghi; khoá chính 2 cột chặn gắn trùng.
-- AC3: amenity_id không ON DELETE CASCADE nên database cũng chặn xoá tiện nghi đang được gắn.
CREATE TABLE room_type_amenities (
    room_type_id INTEGER NOT NULL REFERENCES room_types(id) ON DELETE CASCADE,
    amenity_id BIGINT NOT NULL REFERENCES amenities(id),
    PRIMARY KEY (room_type_id, amenity_id)
);

INSERT INTO amenities (code, name, icon) VALUES
    ('DIEU_HOA',  'Điều hoà',        '❄️'),
    ('WIFI',      'Wi-Fi miễn phí',  '📶'),
    ('BAN_CONG',  'Ban công',        '🌇'),
    ('TIVI',      'Tivi',            '📺'),
    ('NONG_LANH', 'Bình nóng lạnh',  '🚿'),
    ('TU_LANH',   'Tủ lạnh mini',    '🧊')
ON CONFLICT DO NOTHING;

-- Gắn sẵn tiện nghi cho 3 loại phòng mẫu ở V14 để có dữ liệu thử.
INSERT INTO room_type_amenities (room_type_id, amenity_id)
SELECT rt.id, a.id
FROM room_types rt
JOIN amenities a ON
       (rt.code = 'DON'      AND a.code IN ('DIEU_HOA', 'WIFI', 'NONG_LANH'))
    OR (rt.code = 'DOI'      AND a.code IN ('DIEU_HOA', 'WIFI', 'TIVI', 'NONG_LANH'))
    OR (rt.code = 'GIA_DINH' AND a.code IN ('DIEU_HOA', 'WIFI', 'TIVI', 'NONG_LANH', 'TU_LANH', 'BAN_CONG'))
ON CONFLICT DO NOTHING;