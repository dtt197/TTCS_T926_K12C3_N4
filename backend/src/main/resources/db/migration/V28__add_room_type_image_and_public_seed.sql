-- S2-03 Lát 1: ảnh đại diện + văn bản thay thế cho loại phòng, và 4 loại phòng mẫu cho trang công khai.
ALTER TABLE room_types
    ADD COLUMN IF NOT EXISTS image_url VARCHAR(500),
    ADD COLUMN IF NOT EXISTS image_alt VARCHAR(200);

INSERT INTO room_types
    (code, name, standard_capacity, max_capacity, number_of_beds, description, status,
     weekday_price, weekend_price, image_url, image_alt)
VALUES
    ('S203_STD', 'Standard', 2, 2, 1,
     'Phòng tiêu chuẩn cho 2 khách', TRUE,
     450000, 550000, '/room-images/room-1.jpg', 'Phòng Standard với giường đôi'),
    ('S203_DLX', 'Deluxe hướng vườn', 2, 3, 1,
     'Phòng rộng, cửa sổ nhìn ra vườn', TRUE,
     650000, 800000, '/room-images/room-2.jpg', 'Phòng Deluxe nhìn ra vườn'),
    ('S203_FAM', 'Phòng gia đình cao cấp có ban công hướng núi và bếp riêng cho nhóm bạn đông người', 4, 6, 3,
     'Phòng lớn cho nhóm đông người', TRUE,
     1200000, 1500000, '/room-images/room-3.jpg', 'Phòng gia đình có ban công hướng núi'),
    ('S203_VIP', 'VIP', 2, 2, 1,
     'Phòng cao cấp nhất', TRUE,
     1800000, 2200000, '/room-images/room-4.jpg', 'Phòng VIP')
ON CONFLICT DO NOTHING;