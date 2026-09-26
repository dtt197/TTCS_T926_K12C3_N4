CREATE TABLE IF NOT EXISTS rooms (
    id BIGSERIAL PRIMARY KEY,
    room_number VARCHAR(20) NOT NULL UNIQUE,
    floor INTEGER NOT NULL,
    room_type VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'TRONG_SACH',
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT rooms_status_check
        CHECK (status IN ('TRONG_SACH', 'TRONG_BAN', 'DANG_O', 'BAO_TRI'))
);

CREATE TABLE IF NOT EXISTS check_ins (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES rooms(id),
    guest_name VARCHAR(120) NOT NULL,
    checked_in_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rooms_status ON rooms(status);
CREATE INDEX IF NOT EXISTS idx_check_ins_room_id ON check_ins(room_id);

INSERT INTO rooms (room_number, floor, room_type, status)
VALUES
    ('101', 1, 'Phòng đôi', 'TRONG_SACH'),
    ('102', 1, 'Phòng đôi', 'TRONG_BAN'),
    ('201', 2, 'Phòng gia đình', 'DANG_O'),
    ('202', 2, 'Phòng đơn', 'BAO_TRI')
ON CONFLICT (room_number) DO NOTHING;
