-- S3-02 Lát 1: mỗi booking giữ đúng một phòng, cơ sở dữ liệu chặn hai booking cùng chiếm một phòng trong cùng một đêm.
-- Khoảng ngày nửa mở [check_in_date, check_out_date): booking trả phòng ngày 23 và booking nhận phòng ngày 23 không trùng.
-- Chỉ booking đang chiếm phòng (chờ xác nhận, đã xác nhận, đã nhận phòng) bị ràng buộc; đã huỷ, hết hạn, đã trả phòng thì không.

CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE bookings ADD COLUMN room_id BIGINT REFERENCES rooms(id);

CREATE INDEX idx_bookings_room_id ON bookings (room_id);

-- Booking chờ xác nhận đã quá hạn giữ chỗ chuyển sang hết hạn (giống job S2-07 Lát 4) để không chiếm phòng.
UPDATE bookings
SET status = 'DA_HET_HAN'
WHERE status = 'CHO_XAC_NHAN'
  AND hold_expires_at IS NOT NULL
  AND hold_expires_at <= CURRENT_TIMESTAMP;

-- Gán phòng cho booking hiện có: booking tạo trước được gán trước, chọn phòng cùng loại có số phòng nhỏ nhất còn trống.
-- Booking không còn phòng nào (đang bị đặt trùng) để trống room_id và in thông báo để rà soát.
DO $$
DECLARE
    b RECORD;
    chosen_room_id BIGINT;
BEGIN
    FOR b IN
        SELECT bk.id, bk.booking_code, bk.check_in_date, bk.check_out_date, rt.name AS room_type_name
        FROM bookings bk
        JOIN room_types rt ON rt.id = bk.room_type_id
        WHERE bk.status IN ('CHO_XAC_NHAN', 'DA_XAC_NHAN', 'DA_NHAN_PHONG')
        ORDER BY bk.created_at, bk.id
    LOOP
        SELECT r.id INTO chosen_room_id
        FROM rooms r
        WHERE r.active
          AND lower(r.room_type) = lower(b.room_type_name)
          AND NOT EXISTS (
              SELECT 1
              FROM bookings other
              WHERE other.room_id = r.id
                AND other.status IN ('CHO_XAC_NHAN', 'DA_XAC_NHAN', 'DA_NHAN_PHONG')
                AND other.check_in_date < b.check_out_date
                AND other.check_out_date > b.check_in_date)
        ORDER BY r.room_number
        LIMIT 1;

        IF chosen_room_id IS NULL THEN
            RAISE NOTICE 'Booking % chua gan duoc phong vi trung dem voi booking khac, can ra soat', b.booking_code;
        ELSE
            UPDATE bookings SET room_id = chosen_room_id WHERE id = b.id;
        END IF;
    END LOOP;
END $$;

ALTER TABLE bookings
    ADD CONSTRAINT bookings_room_no_overlap
    EXCLUDE USING gist (
        room_id WITH =,
        daterange(check_in_date, check_out_date, '[)') WITH &&
    )
    WHERE (status IN ('CHO_XAC_NHAN', 'DA_XAC_NHAN', 'DA_NHAN_PHONG'));
