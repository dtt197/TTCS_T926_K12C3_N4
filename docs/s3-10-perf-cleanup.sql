-- S3-10 Lát 4: xoá dữ liệu thử hiệu năng sơ đồ phòng (tạo bởi docs/s3-10-perf-seed.sql).
DELETE FROM bookings WHERE booking_code LIKE 'PERF-__-_';
DELETE FROM rooms WHERE room_number LIKE 'PERF-__';