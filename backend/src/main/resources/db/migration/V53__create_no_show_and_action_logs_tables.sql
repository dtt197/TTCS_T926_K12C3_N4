-- Migration V53: Khách không đến (No-show) và lịch sử thao tác
-- S4-04 (SCRUM-149): Cho phép Lễ tân đánh dấu khách không đến khi đủ điều kiện thời gian để giải phóng lịch/phòng

CREATE TABLE IF NOT EXISTS customers (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(120),
    phone VARCHAR(30),
    email VARCHAR(150),
    no_show_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE customers
    ADD COLUMN IF NOT EXISTS no_show_count INT NOT NULL DEFAULT 0;

-- Cập nhật ràng buộc trạng thái booking cho phép NO_SHOW và KHONG_DEN
ALTER TABLE bookings
    DROP CONSTRAINT IF EXISTS bookings_status_check;

ALTER TABLE bookings
    ADD CONSTRAINT bookings_status_check
    CHECK (
        status IN (
            'CHO_XAC_NHAN',
            'DA_XAC_NHAN',
            'DA_HUY',
            'DA_NHAN_PHONG',
            'DA_TRA_PHONG',
            'DA_HET_HAN',
            'NO_SHOW',
            'KHONG_DEN'
        )
    );

-- Bảng appointment_action_logs ghi nhận lịch sử thao tác của lễ tân
CREATE TABLE IF NOT EXISTS appointment_action_logs (
    id BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL,
    receptionist_id BIGINT,
    action_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appointment_action_logs_booking FOREIGN KEY (appointment_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_appointment_action_logs_receptionist FOREIGN KEY (receptionist_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_appointment_action_logs_appointment_id ON appointment_action_logs(appointment_id);
CREATE INDEX IF NOT EXISTS idx_appointment_action_logs_created_at ON appointment_action_logs(created_at DESC);
