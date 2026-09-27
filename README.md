# Module: Tiện nghi & Tham số homestay (Thành viên số 8)

Bám theo bảng phân công Sprint 1:
- **SCRUM-24** — Quản lý tiện nghi (CRUD) → `controllers/amenityController.js`, `routes/amenityRoutes.js`
- **SCRUM-29** — Gắn tiện nghi vào loại phòng → `controllers/roomTypeAmenityController.js`
- **SCRUM-32** — Cấu hình giờ nhận/trả phòng → `controllers/roomTypeConfigController.js`
- **SCRUM-34** — Cấu hình phụ thu → cùng file trên (early/late/extra-guest surcharge)
- **SCRUM-36** — Chính sách hủy → cùng file trên (cancellation_policy_type, deadline, refund %)
- **SCRUM-37** — Ghi lịch sử thay đổi → `middlewares/logHistory.js` (gọi ở mọi create/update/delete)
- **SCRUM-38** — Xem lịch sử thay đổi → `controllers/historyController.js`

## Cài đặt

```bash
npm install
# tạo database PostgreSQL rồi chạy lần lượt các file trong /migrations
psql -U postgres -d homestay_db -f migrations/001_create_amenities.sql
psql -U postgres -d homestay_db -f migrations/002_create_room_type_amenities.sql
psql -U postgres -d homestay_db -f migrations/003_create_room_type_configs.sql
psql -U postgres -d homestay_db -f migrations/004_create_config_histories.sql

# cấu hình biến môi trường (hoặc sửa mặc định trong models/index.js)
export DB_HOST=localhost DB_NAME=homestay_db DB_USER=postgres DB_PASSWORD=postgres

npm start
```

## API chính

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | /api/amenities | Danh sách tiện nghi (lọc theo category, status) |
| POST | /api/amenities | Tạo tiện nghi |
| PUT | /api/amenities/:id | Sửa tiện nghi |
| DELETE | /api/amenities/:id | Xóa mềm tiện nghi |
| GET | /api/room-types/:roomTypeId/amenities | Danh sách tiện nghi của 1 loại phòng |
| POST | /api/room-types/:roomTypeId/amenities | Gắn nhiều tiện nghi vào loại phòng |
| DELETE | /api/room-types/:roomTypeId/amenities/:amenityId | Gỡ tiện nghi khỏi loại phòng |
| GET | /api/room-types/:roomTypeId/config | Lấy cấu hình giờ/phụ thu/hủy phòng |
| PUT | /api/room-types/:roomTypeId/config | Tạo/cập nhật cấu hình (upsert) |
| GET | /api/history?entity_type=&entity_id= | Xem lịch sử thay đổi |

## Phụ thuộc cần lưu ý (theo đúng cột "Phụ thuộc, thứ tự" trong bảng phân công)

1. **SCRUM-32 phụ thuộc mô hình loại phòng của Người 6** — hiện `room_type_id` trong
   `room_type_amenities` và `room_type_configs` chưa có khóa ngoại cứng tới bảng
   `room_types`, để 2 module có thể làm song song. Khi Người 6 chốt schema
   `room_types`, chạy thêm:
   ```sql
   ALTER TABLE room_type_amenities ADD CONSTRAINT fk_room_type
     FOREIGN KEY (room_type_id) REFERENCES room_types(id) ON DELETE CASCADE;
   ALTER TABLE room_type_configs ADD CONSTRAINT fk_room_type
     FOREIGN KEY (room_type_id) REFERENCES room_types(id) ON DELETE CASCADE;
   ```
2. **CRUD tiện nghi (SCRUM-24) và cấu hình (SCRUM-32/34/36) có thể bắt đầu song song**
   — đúng như ghi chú trong bảng, 2 controller này không phụ thuộc lẫn nhau.
3. **Xác thực (auth)**: các route hiện chưa gắn middleware xác thực thật — cần thay
   dòng `// app.use(authMiddleware)` trong `app.js` bằng middleware của Người 1 để
   `req.user.id` có giá trị thật khi ghi lịch sử (`changed_by`).

## Việc còn lại / cần chốt cùng team

- Thống nhất chính xác tên bảng/field của `room_types` với Người 6 trước khi thêm FK cứng.
- Thống nhất cơ chế lấy `user_id` hiện tại (`req.user`) với module xác thực của Người 1.
- Quyết định: phụ thu/chính sách hủy áp dụng theo **loại phòng** (đang làm) hay theo
  **từng homestay** — nếu PO muốn cấu hình ở cấp homestay thay vì loại phòng, chỉ cần
  đổi `room_type_id` → `homestay_id` trong `RoomTypeConfig`, phần còn lại giữ nguyên.
