ALTER TABLE room_types
    ADD COLUMN extra_guest_fee BIGINT NOT NULL DEFAULT 200000;

UPDATE room_types
SET extra_guest_fee = COALESCE(
    extra_person_fee,
    (SELECT extra_person_fee
     FROM operating_settings
     ORDER BY created_at DESC, id DESC
     LIMIT 1),
    200000
);

ALTER TABLE room_types
    ADD CONSTRAINT room_types_extra_guest_fee_check
    CHECK (extra_guest_fee >= 0);
