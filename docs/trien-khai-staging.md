# Triển khai staging bằng Docker Compose (S4-10)

Hệ thống gồm 3 phần chạy cùng lúc bằng một lệnh: cơ sở dữ liệu PostgreSQL 15, backend Spring Boot, giao diện React (phục vụ bằng nginx).
Máy chạy staging: máy của nhóm (theo phương án dự phòng trong backlog, do không được cấp máy chủ). Mọi người mở bằng trình duyệt qua `http://<IP máy chạy>:<APP_PORT>`.

## 1. Chuẩn bị máy chạy staging (làm một lần)
1. Cài Docker Desktop, bật lên.
2. Lấy mã nguồn: `git clone <địa chỉ repo>` rồi `git checkout develop`.
3. Ở thư mục gốc repo, sao chép `.env.example` thành `.env`, điền:
   - `DB_PASSWORD`: mật khẩu mạnh mới.
   - `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`: mỗi khoá chạy `openssl rand -base64 48` một lần rồi dán kết quả.
   - `PUBLIC_URL`: `http://<IP máy>:<APP_PORT>` (xem IP bằng `ipconfig`, dòng IPv4 Address).
   - Không gửi file `.env` qua chat, không đưa lên GitHub.

## 2. Khởi chạy
```bash
docker compose up -d --build
```
- Lần đầu mất vài phút để build. Kiểm tra: `docker compose ps` (cả 3 dịch vụ ở trạng thái running).
- Xem log backend: `docker compose logs -f backend` (chờ dòng `Started HomestayApplication`).
- Kiểm tra tình trạng: mở `http://localhost:<APP_PORT>/actuator/health` → `"status":"UP"` và `"db":{"status":"UP"...}`.

## 3. Nạp dữ liệu mẫu cho nghiệm thu
1. Mở `http://localhost:<APP_PORT>`, bấm ô **Chủ homestay** trong "Tài khoản mẫu thử nghiệm" để đăng nhập.
2. Menu **Dữ liệu mẫu** → **Nạp lại dữ liệu mẫu** (4 loại phòng, 20 phòng, bảng giá, 30 booking).

## 4. Cho máy khác truy cập
- Các máy cùng mạng Wi-Fi mở `http://<IP máy chạy>:<APP_PORT>`.
- Nếu không mở được: cho phép Docker Desktop qua Windows Defender Firewall (mạng Private).

## 5. Cập nhật phiên bản mới
```bash
git pull origin develop
docker compose up -d --build
```
Dữ liệu và ảnh vẫn giữ nguyên (nằm trong volume `db-data` và `uploads`).

## 6. Dừng / xoá
- Dừng: `docker compose down` (dữ liệu vẫn giữ).
- Xoá cả dữ liệu để làm lại từ đầu: `docker compose down -v` (cẩn thận, mất hết dữ liệu staging).

## 7. Biến môi trường
Xem `.env.example`. Thiếu biến bắt buộc (`DB_PASSWORD`, `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET`) thì `docker compose` dừng và báo tên biến; chạy backend trực tiếp cũng dừng với thông báo `Could not resolve placeholder '<TÊN BIẾN>'`.

## 8. Chạy trên máy cá nhân khi phát triển (không dùng Docker)
- Cần khai báo biến môi trường `DB_PASSWORD`, `JWT_ACCESS_SECRET`, `JWT_REFRESH_SECRET` (ví dụ `setx` trên Windows), hoặc chạy chế độ H2: `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`.
- Frontend: `npm run dev`, gọi API qua proxy của Vite.

## 9. Lưu ý bảo mật
- Mật khẩu và khoá từng nằm trong kho mã (giá trị mặc định cũ trong `application.properties`) vẫn còn trong lịch sử git, nên **phải dùng giá trị mới** trong `.env` của staging và đổi mật khẩu DB trên các máy cá nhân.
- 4 tài khoản mẫu (S4-09) dùng mật khẩu mặc định công khai để phục vụ nghiệm thu; cần PO quyết định có tắt sau nghiệm thu không.