-- Sprint 3 SCRUM-96 (S3-04): Lịch sử thay đổi booking (Booking Audit Log)
CREATE TABLE booking_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    booking_code VARCHAR(40),
    old_check_in_date DATE NOT NULL,
    new_check_in_date DATE NOT NULL,
    old_check_out_date DATE NOT NULL,
    new_check_out_date DATE NOT NULL,
    old_room_type_id BIGINT,
    old_room_type_name VARCHAR(100) NOT NULL,
    new_room_type_id BIGINT,
    new_room_type_name VARCHAR(100) NOT NULL,
    old_total_amount BIGINT NOT NULL,
    new_total_amount BIGINT NOT NULL,
    actor_user_id BIGINT,
    actor_name VARCHAR(150),
    actor_email VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_booking_audit_logs_booking
        FOREIGN KEY (booking_id) REFERENCES bookings(id) ON DELETE CASCADE,
    CONSTRAINT fk_booking_audit_logs_actor
        FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_booking_audit_logs_booking_id ON booking_audit_logs(booking_id);
CREATE INDEX idx_booking_audit_logs_created_at ON booking_audit_logs(created_at DESC);
