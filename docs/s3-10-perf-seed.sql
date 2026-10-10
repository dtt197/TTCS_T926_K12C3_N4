-- S3-10 Lát 4: dữ liệu thử hiệu năng sơ đồ phòng. CHỈ chạy trên database ở máy mình.
-- Tạo 40 phòng PERF-01..PERF-40 và 160 booking (mỗi phòng 4 booking 2–3 đêm) trong 14 ngày tới.
-- Phòng thuộc loại "Thử hiệu năng" (không có loại phòng nào trùng tên) nên không ảnh hưởng số phòng bán cho khách.
-- Dọn dữ liệu bằng docs/s3-10-perf-cleanup.sql sau khi nghiệm thu.

INSERT INTO rooms (room_number, floor, room_type, status, active)
SELECT 'PERF-' || lpad(n::text, 2, '0'), 9, 'Thử hiệu năng', 'TRONG_SACH', TRUE
FROM generate_series(1, 40) AS n;

INSERT INTO bookings (room_type_id, room_type_name_snapshot, check_in_date, check_out_date,
       weekday_price_snapshot, weekend_price_snapshot, weekend_days_snapshot, total_amount,
       booking_code, guest_name, status, room_id)
SELECT NULL, 'Thử hiệu năng',
       CURRENT_DATE + (k * 4 + n % 3),
       CURRENT_DATE + (k * 4 + n % 3) + 2 + (n % 2),
       500000, 700000, 'FRIDAY,SATURDAY', 1000000,
       'PERF-' || lpad(n::text, 2, '0') || '-' || k,
       'Khách thử ' || n || '.' || k,
       'DA_XAC_NHAN', r.id
FROM generate_series(1, 40) AS n
CROSS JOIN generate_series(0, 3) AS k
JOIN rooms r ON r.room_number = 'PERF-' || lpad(n::text, 2, '0');