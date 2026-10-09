ALTER TABLE bookings
    ADD COLUMN room_confirmed_at TIMESTAMPTZ NULL,
    ADD COLUMN room_confirmed_by_user_id BIGINT NULL;

ALTER TABLE bookings
    ADD CONSTRAINT fk_bookings_room_confirmed_by_user
        FOREIGN KEY (room_confirmed_by_user_id) REFERENCES users (id);
