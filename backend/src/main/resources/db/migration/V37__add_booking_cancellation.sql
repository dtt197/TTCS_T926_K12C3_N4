-- S3-02 Lát 3: lưu lý do, người huỷ và thời điểm huỷ booking.
ALTER TABLE bookings
    ADD COLUMN cancel_reason VARCHAR(500),
    ADD COLUMN cancelled_by VARCHAR(255),
    ADD COLUMN cancelled_at TIMESTAMPTZ;