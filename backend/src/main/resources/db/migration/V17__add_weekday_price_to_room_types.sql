ALTER TABLE room_types
ADD COLUMN weekday_price BIGINT;

ALTER TABLE room_types
ADD CONSTRAINT chk_room_types_weekday_price
CHECK (weekday_price IS NULL OR weekday_price > 0);
