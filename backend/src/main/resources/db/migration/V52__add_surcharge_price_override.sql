-- S4-02 Lát 4: lưu giá mặc định tại thời điểm thêm, lý do và người sửa khi đơn giá được nhập tay.
ALTER TABLE booking_surcharges ADD COLUMN default_unit_price BIGINT;
UPDATE booking_surcharges SET default_unit_price = unit_price;
ALTER TABLE booking_surcharges ALTER COLUMN default_unit_price SET NOT NULL;
ALTER TABLE booking_surcharges
    ADD CONSTRAINT chk_booking_surcharges_default_unit_price CHECK (default_unit_price >= 0);

ALTER TABLE booking_surcharges ADD COLUMN price_override_reason VARCHAR(500);
ALTER TABLE booking_surcharges ADD COLUMN price_overridden_by VARCHAR(120);

-- Đơn giá khác giá mặc định thì bắt buộc có lý do.
ALTER TABLE booking_surcharges
    ADD CONSTRAINT chk_booking_surcharges_override_reason CHECK (
        unit_price = default_unit_price
        OR (price_override_reason IS NOT NULL AND char_length(btrim(price_override_reason)) > 0));