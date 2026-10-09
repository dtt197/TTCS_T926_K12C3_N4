CREATE TABLE booking_room_change_history (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL REFERENCES bookings (id) ON DELETE CASCADE,
    old_room_id BIGINT NOT NULL REFERENCES rooms (id),
    new_room_id BIGINT NOT NULL REFERENCES rooms (id),
    actor_user_id BIGINT NOT NULL REFERENCES users (id),
    changed_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(500) NOT NULL,
    CONSTRAINT booking_room_change_history_distinct_rooms CHECK (old_room_id <> new_room_id),
    CONSTRAINT booking_room_change_history_reason_nonblank CHECK (length(btrim(reason)) > 0)
);

CREATE INDEX idx_booking_room_change_history_booking_changed
    ON booking_room_change_history (booking_id, changed_at DESC, id DESC);
CREATE INDEX idx_booking_room_change_history_old_room ON booking_room_change_history (old_room_id);
CREATE INDEX idx_booking_room_change_history_new_room ON booking_room_change_history (new_room_id);
CREATE INDEX idx_booking_room_change_history_actor ON booking_room_change_history (actor_user_id);
