-- SCRUM-12: ghi chú riêng của phòng.
-- Không thay đổi bảng rooms hiện có.
CREATE TABLE room_notes (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT NOT NULL UNIQUE REFERENCES rooms(id) ON DELETE CASCADE,
    note VARCHAR(500) NOT NULL,
    CONSTRAINT room_notes_note_not_blank CHECK (btrim(note) <> '')
);
