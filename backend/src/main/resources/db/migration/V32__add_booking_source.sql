-- S3-06: Phân biệt nguồn booking
-- TRUC_TUYEN: booking được tạo từ hệ thống đặt phòng trực tuyến
-- TAI_QUAY: booking được tạo tại quầy lễ tân

ALTER TABLE bookings
ADD COLUMN IF NOT EXISTS source VARCHAR(30);

-- Các booking cũ được xem là booking trực tuyến
UPDATE bookings
SET source = 'TRUC_TUYEN'
WHERE source IS NULL;

-- Không cho phép booking mới không có nguồn
ALTER TABLE bookings
ALTER COLUMN source SET NOT NULL;

-- Mặc định cho các booking trực tuyến tạo sau này
ALTER TABLE bookings
ALTER COLUMN source SET DEFAULT 'TRUC_TUYEN';