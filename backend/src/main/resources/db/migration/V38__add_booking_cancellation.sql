-- V30: Thông tin huỷ booking và hoàn cọc (S3-05 Lát 1)
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(40);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS cancel_note VARCHAR(500);
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS cancel_deposit_amount BIGINT;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS cancel_refund_percent INT;
ALTER TABLE bookings ADD COLUMN IF NOT EXISTS cancel_refund_amount BIGINT;