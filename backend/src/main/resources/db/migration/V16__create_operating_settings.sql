-- S1-09: thông tin homestay và tham số vận hành.
-- AC4: mỗi lần lưu tạo MỘT PHIÊN BẢN MỚI (không sửa đè), ghi người sửa và thời điểm.
-- Booking (Sprint 2) sẽ lấy phiên bản có hiệu lực tại lúc tạo booking, nên đổi tham số không ảnh hưởng booking cũ.
CREATE TABLE operating_settings (
    id BIGSERIAL PRIMARY KEY,
    homestay_name VARCHAR(150) NOT NULL,
    address VARCHAR(255),
    phone VARCHAR(20),
    email VARCHAR(255),
    check_in_time TIME NOT NULL,
    check_out_time TIME NOT NULL,
    late_checkout_fee_per_hour BIGINT NOT NULL,
    extra_person_fee BIGINT NOT NULL,
    created_by_user_id BIGINT REFERENCES users(id),
    created_by_name VARCHAR(150) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT operating_settings_fees_check
        CHECK (late_checkout_fee_per_hour >= 0 AND extra_person_fee >= 0)
);

CREATE INDEX idx_operating_settings_created_at ON operating_settings (created_at DESC);

-- AC2: mỗi phiên bản có tối đa 3 mốc huỷ (kiểm tra số mốc ở tầng ứng dụng).
-- AC3: trong cùng một phiên bản không có 2 mốc trùng số giờ.
CREATE TABLE cancellation_policy_tiers (
    id BIGSERIAL PRIMARY KEY,
    settings_id BIGINT NOT NULL REFERENCES operating_settings(id) ON DELETE CASCADE,
    hours_before_check_in INTEGER NOT NULL,
    refund_percent INTEGER NOT NULL,
    CONSTRAINT cancellation_tiers_hours_check CHECK (hours_before_check_in >= 0),
    CONSTRAINT cancellation_tiers_percent_check CHECK (refund_percent BETWEEN 0 AND 100),
    CONSTRAINT cancellation_tiers_unique_hours UNIQUE (settings_id, hours_before_check_in)
);

-- Phiên bản mặc định theo Excel: nhận phòng 14:00, trả phòng 12:00.
INSERT INTO operating_settings (homestay_name, check_in_time, check_out_time,
        late_checkout_fee_per_hour, extra_person_fee, created_by_name)
VALUES ('HomeStay', '14:00', '12:00', 100000, 200000, 'Hệ thống');

-- Chính sách huỷ mặc định: trước 72 giờ hoàn 100%, trước 24 giờ hoàn 50%, sát ngày hoàn 0%.
INSERT INTO cancellation_policy_tiers (settings_id, hours_before_check_in, refund_percent)
SELECT settings.id, tier.hours, tier.percent
FROM operating_settings settings,
     (VALUES (72, 100), (24, 50), (0, 0)) AS tier(hours, percent);