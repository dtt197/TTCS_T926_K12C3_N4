# TTCS_T926_K12C3_N4

## Chức năng cập nhật trạng thái phòng — S1-10

### Công nghệ

- Frontend: React 18 + TypeScript + Vite.
- Backend: Spring Boot + Java 17 + Spring Data JPA.
- Database: PostgreSQL 15.
- Test backend: JUnit 5 và H2 in-memory.

### Phạm vi đã hoàn thành

- Hiển thị danh sách phòng và trạng thái hiện tại.
- Có đủ bốn trạng thái: `TRONG_SACH`, `TRONG_BAN`, `DANG_O`, `BAO_TRI`.
- Lễ tân đổi được trạng thái phòng.
- Khi chuyển sang `BAO_TRI`, bắt buộc nhập lý do, ngày bắt đầu và ngày kết thúc.
- Danh sách phòng hiển thị lại chính xác lý do và khoảng ngày bảo trì.
- Chỉ phòng `TRONG_SACH` mới nhận phòng được.
- Trả lỗi HTTP 409 bằng tiếng Việt khi trạng thái không phù hợp.
- Không cho chuyển trực tiếp phòng `DANG_O` sang `BAO_TRI`; cần trả phòng trước.
- Có dữ liệu mẫu bốn phòng trong `database/001_room_status.sql`.

> Theo phạm vi story hiện tại, chưa có lịch sử thay đổi trạng thái. Lý do và khoảng ngày bảo trì đã được bổ sung trong story này.

### Chạy backend

1. Tạo database PostgreSQL và tài khoản local.
2. Chạy migration và dữ liệu mẫu:

   ```bash
   psql -h localhost -U homestay_app -d homestay_db -f database/001_room_status.sql
   ```

3. Khai báo biến môi trường, không commit mật khẩu:

   ```bash
   export DB_URL=jdbc:postgresql://localhost:5432/homestay_db
   export DB_USERNAME=homestay_app
   export DB_PASSWORD='<mat-khau-local>'
   ```

4. Chạy Spring Boot:

   ```bash
   cd backend
   bash mvnw spring-boot:run
   ```

   API chính:

   - `GET /api/rooms`
   - `PATCH /api/rooms/{roomId}/status`
   - `PATCH /api/rooms/{roomId}/maintenance`
   - `POST /api/rooms/{roomId}/check-in`

### Chạy frontend

```bash
cd frontend
cp .env.example .env.local
npm ci
npm run dev
```

Mặc định frontend gọi `http://localhost:8080/api`. Nếu backend chưa chạy, giao diện tự chuyển sang dữ liệu demo để có thể trình diễn luồng.

### Kịch bản kiểm thử acceptance

1. Mở màn hình vận hành phòng, kiểm tra bốn thẻ tổng quan và danh sách phòng.
2. Đổi lần lượt một phòng qua `Trống sạch`, `Trống bẩn`, `Đang ở`, `Bảo trì`; sau mỗi lần bấm **Lưu trạng thái**, badge và số tổng quan phải cập nhật.
3. Chọn phòng `Trống sạch`, nhập tên khách, bấm **Xác nhận nhận phòng**. Kết quả thành công: phòng chuyển sang `Đang ở`.
4. Chọn phòng `Trống bẩn`, `Đang ở` hoặc `Bảo trì`. Nút nhận phòng bị khoá và hiển thị lý do; nếu gọi API trực tiếp vẫn nhận HTTP 409.
5. Thử chuyển phòng `Đang ở` sang `Bảo trì`; API từ chối và yêu cầu trả phòng trước.
6. Chọn phòng `Trống sạch` hoặc `Trống bẩn`, chọn `Bảo trì`, nhập lý do và khoảng ngày, sau đó lưu. Danh sách phải hiển thị lại đủ ba thông tin này.
7. Thử lưu khi thiếu lý do, thiếu một trong hai ngày hoặc ngày kết thúc trước ngày bắt đầu; hệ thống phải chặn lưu.

### Quy trình Git

Không push trực tiếp vào `main`. Làm việc trên feature branch và tạo Pull Request vào `develop`:

```bash
git checkout develop
git pull origin develop
git checkout -b feature/room-status-check-in
git add backend frontend database README.md
git commit -m "feat: manage room status and restrict check-in"
git push -u origin feature/room-status-check-in
```