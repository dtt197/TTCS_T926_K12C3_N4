-- S2-06 Lát 2: phụ thu thêm người khi số khách vượt sức chứa tiêu chuẩn (mỗi người vượt, mỗi đêm).
-- Lưu lại số người vượt, mức phụ thu áp dụng và tiền phụ thu để tổng tiền booking khớp với tạm tính khách đã thấy.
ALTER TABLE bookings
    ADD COLUMN extra_guest_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN extra_person_fee_snapshot BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN surcharge_amount BIGINT NOT NULL DEFAULT 0;

ALTER TABLE bookings
    ADD CONSTRAINT bookings_surcharge_check
    CHECK (extra_guest_count >= 0 AND extra_person_fee_snapshot >= 0 AND surcharge_amount >= 0);