-- S1-10: bắt buộc lưu lý do và khoảng ngày khi phòng vào bảo trì.
-- Dùng cho database đã chạy migration 001 trước đó.

ALTER TABLE rooms
    ADD COLUMN IF NOT EXISTS maintenance_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS maintenance_start_date DATE,
    ADD COLUMN IF NOT EXISTS maintenance_end_date DATE;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'rooms_maintenance_period_check'
    ) THEN
        ALTER TABLE rooms
            ADD CONSTRAINT rooms_maintenance_period_check
            CHECK (
                maintenance_start_date IS NULL
                OR maintenance_end_date IS NULL
                OR maintenance_end_date >= maintenance_start_date
            );
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_rooms_maintenance_period
    ON rooms(maintenance_start_date, maintenance_end_date);