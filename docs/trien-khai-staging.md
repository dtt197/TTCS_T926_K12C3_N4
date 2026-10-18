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
- Nhân viên đăng nhập qua `http://<IP>:<APP_PORT>/quan-tri` (đường dẫn `/` là trang chủ cho khách).
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

## 10. Tự khởi động lại và giữ dữ liệu (S4-10 Lát 2)
### Cài đặt một lần trên máy chạy staging
- Docker Desktop → **Settings → General** → bật **"Start Docker Desktop when you sign in to your computer"** → **Apply & restart**.
- Khi Docker khởi động, cả 3 dịch vụ tự chạy lại nhờ `restart: unless-stopped` (không cần gõ lệnh).
- **Không** chạy `docker compose down` trước khi tắt máy (lệnh này xoá container, khởi động lại máy sẽ không tự chạy). Muốn tắt hẳn hệ thống mới dùng `down`.
- Trên Windows, Docker Desktop chỉ chạy sau khi có người **đăng nhập Windows**. Máy chủ Linux thì bật `sudo systemctl enable docker` để Docker chạy ngay khi bật máy.

### Thứ tự khởi động
`db` (chờ `pg_isready`) → `backend` (chờ db healthy, tự khởi động lại khi db khởi động lại; healthy khi `/actuator/health` = UP) → `frontend` (chờ backend healthy).
Xem trạng thái: `docker compose ps` (cột STATUS có `healthy`).

### Dữ liệu và ảnh
Nằm trong volume `homestay_db-data` và `homestay_uploads`, còn nguyên khi container bị dừng, tạo lại (`docker compose up -d --force-recreate`) hoặc khi khởi động lại máy. Chỉ mất khi chạy `docker compose down -v`.

### Xem nhật ký (log) sau khi khởi động lại
- Log từng dịch vụ: `docker compose logs --tail 200 backend` (tương tự `db`, `frontend`), theo dõi liên tục thêm `-f`. Mỗi dịch vụ giữ tối đa 5 tệp × 10 MB.
- Log backend còn ghi ra tệp trong volume `homestay_backend-logs`: `docker compose exec backend tail -n 200 /app/logs/homestay.log` (trên Git Bash thêm `MSYS_NO_PATHCONV=1` ở đầu lệnh để không bị đổi đường dẫn).
- Số lần dịch vụ tự chạy lại: `docker inspect -f "{{.RestartCount}}" homestay-backend-1`.

## 11. Sao lưu cơ sở dữ liệu tự động (S4-10 Lát 3)
- Dịch vụ `backup` chạy `pg_dump` lúc **2:00 hằng ngày** (giờ Asia/Ho_Chi_Minh), lưu tệp nén `homestay_YYYYMMDD_HHMMSS.dump` vào thư mục `backups/` (đổi bằng `BACKUP_DIR`), **giữ 7 bản** mới nhất (`BACKUP_KEEP`).
- Chỉ xoá bản cũ khi lần sao lưu mới **thành công**; sao lưu lỗi thì giữ nguyên các bản cũ.
- Nhật ký: `backups/backup.log` (mỗi dòng: bắt đầu | kết thúc | THANH_CONG/THAT_BAI | tệp | dung lượng), hoặc `docker compose logs backup`.
- Chạy sao lưu ngay bằng tay: `docker compose exec backup backup.sh`.
- Đổi lịch (ví dụ để thử): đặt `BACKUP_SCHEDULE` trong `.env` (cú pháp cron: `phút giờ * * *`) rồi `docker compose up -d backup`. Nhớ trả về `0 2 * * *`.
- Quyền: trong container thư mục `/backups` là `700`, tệp `600`. Trên máy chủ Linux đặt quyền thư mục sao lưu cho riêng quản trị: `sudo chown root:root backups && sudo chmod 700 backups`.
- Lịch sao lưu tự chạy lại sau khi khởi động lại máy (`restart: unless-stopped`).
- Đã chốt với PO:
  - **Nơi lưu:** thư mục `backups/` trên chính máy chạy staging. Muốn chép sang ổ hoặc máy khác thì đổi `BACKUP_DIR` trong `.env`.
  - **Phạm vi:** chỉ sao lưu cơ sở dữ liệu. Ảnh và tệp người dùng tải lên (volume `uploads`) không nằm trong bản sao lưu.
  - **Khi thất bại:** ghi dòng `THAT_BAI` kèm lý do vào `backups/backup.log` và `docker compose logs backup`, giữ nguyên các bản cũ. Hệ thống không gửi email hay tin nhắn báo lỗi; quản trị kiểm tra nhật ký.