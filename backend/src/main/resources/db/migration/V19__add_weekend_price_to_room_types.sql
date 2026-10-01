ALTER TABLE room_types
ADD COLUMN weekend_price BIGINT;

ALTER TABLE room_types
ADD CONSTRAINT chk_room_types_weekend_price
CHECK (weekend_price IS NULL OR weekend_price > 0);