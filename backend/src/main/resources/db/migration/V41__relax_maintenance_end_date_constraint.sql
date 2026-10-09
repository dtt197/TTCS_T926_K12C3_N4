-- S3-09: Nới lỏng constraint cho phép maintenance_end_date NULL khi BAO_TRI
-- Buồng phòng báo sự cố chỉ cần ghi chú + ngày bắt đầu, lễ tân bổ sung ngày kết thúc sau.
-- Constraint cũ yêu cầu cả maintenance_end_date NOT NULL → gây lỗi 500 khi báo sự cố.

ALTER TABLE rooms DROP CONSTRAINT IF EXISTS rooms_maintenance_details_check;

ALTER TABLE rooms ADD CONSTRAINT rooms_maintenance_details_check
    CHECK (
        status <> 'BAO_TRI'
        OR (
            maintenance_reason IS NOT NULL
            AND btrim(maintenance_reason) <> ''
            AND maintenance_start_date IS NOT NULL
        )
    );
