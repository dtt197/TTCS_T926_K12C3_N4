-- V25: Tạo bảng lưu trữ thông tin ảnh cho từng loại phòng (S2-09)
CREATE TABLE IF NOT EXISTS room_type_images (
    id BIGSERIAL PRIMARY KEY,
    room_type_id BIGINT NOT NULL REFERENCES room_types(id) ON DELETE CASCADE,
    image_url VARCHAR(500) NOT NULL,
    thumbnail_url VARCHAR(500) NOT NULL,
    display_order INT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_room_type_images_room_type_id ON room_type_images(room_type_id);
CREATE INDEX IF NOT EXISTS idx_room_type_images_order ON room_type_images(room_type_id, display_order);
