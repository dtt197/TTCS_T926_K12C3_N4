-- S1-10: Trạng thái phòng, bảo trì và nhận phòng
-- Chuyển từ database/001_room_status.sql + 002_room_maintenance_details.sql sang Flyway
CREATE TABLE rooms (
    id BIGSERIAL PRIMARY KEY,
    room_number VARCHAR(20) NOT NULL UNIQUE,
    floor INTEGER NOT NULL,
    room_type VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'TRONG_SACH',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    maintenance_reason VARCHAR(500),
    maintenance_start_date DATE,
    maintenance_end_date DATE,
    CONSTRAINT rooms_status_check
        CHECK (status IN ('TRONG_SACH', 'TRONG_BAN', 'DANG_O', 'BAO_TRI')),
    CONSTRAINT rooms_maintenance_period_check
        CHECK (maintenance_start_date IS NULL
            OR maintenance_end_date IS NULL
            OR maintenance_end_date >= maintenance_start_date),
    CONSTRAINT rooms_maintenance_details_check
        CHECK (status <> 'BAO_TRI'
            OR (maintenance_reason IS NOT NULL AND btrim(maintenance_reason) <> ''
                AND maintenance_start_date IS NOT NULL AND maintenance_end_date IS NOT NULL))
);

CREATE TABLE check_ins (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES rooms(id),
    guest_name VARCHAR(120) NOT NULL,
    checked_in_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_rooms_status ON rooms(status);
CREATE INDEX idx_rooms_maintenance_period ON rooms(maintenance_start_date, maintenance_end_date);
CREATE INDEX idx_check_ins_room_id ON check_ins(room_id);