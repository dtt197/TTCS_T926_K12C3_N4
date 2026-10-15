-- Migration V54: Thêm trường previous_status để hỗ trợ hoàn tác đánh dấu không đến
-- S4-04 (SCRUM-151): Lưu vết trạng thái trước khi no-show để hoàn tác chính xác trong 15 phút

ALTER TABLE appointment_action_logs
    ADD COLUMN IF NOT EXISTS previous_status VARCHAR(50);
