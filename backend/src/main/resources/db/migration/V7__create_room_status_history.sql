CREATE TABLE IF NOT EXISTS room_status_history (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    previous_status VARCHAR(20) NOT NULL,
    new_status VARCHAR(20) NOT NULL,
    changed_by VARCHAR(150) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    maintenance_reason VARCHAR(500),
    maintenance_start_date DATE,
    maintenance_end_date DATE,
    CONSTRAINT chk_room_status_history_previous_status
        CHECK (previous_status IN ('TRONG_SACH', 'TRONG_BAN', 'DANG_O', 'BAO_TRI')),
    CONSTRAINT chk_room_status_history_new_status
        CHECK (new_status IN ('TRONG_SACH', 'TRONG_BAN', 'DANG_O', 'BAO_TRI'))
);

CREATE INDEX IF NOT EXISTS idx_room_status_history_room_id ON room_status_history (room_id);
CREATE INDEX IF NOT EXISTS idx_room_status_history_changed_at ON room_status_history (changed_at);