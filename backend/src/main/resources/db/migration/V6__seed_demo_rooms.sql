-- Dữ liệu mẫu 4 phòng để demo S1-10
INSERT INTO rooms (room_number, floor, room_type, status,
                   maintenance_reason, maintenance_start_date, maintenance_end_date)
VALUES
    ('101', 1, 'Phòng đôi',      'TRONG_SACH', NULL, NULL, NULL),
    ('102', 1, 'Phòng đôi',      'TRONG_BAN',  NULL, NULL, NULL),
    ('201', 2, 'Phòng gia đình', 'DANG_O',     NULL, NULL, NULL),
    ('202', 2, 'Phòng đơn',      'BAO_TRI',    'Thay điều hòa', '2026-09-01', '2026-09-30');