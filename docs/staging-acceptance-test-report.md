# BIÊN BẢN CHẠY THỬ KỊCH BẢN NGHIỆM THU TRÊN STAGING (S4-09 AC4)
**Mã tài liệu:** BB-NT-S4-09-STAGING
**Dự án:** Hệ thống đặt phòng HomeStay (TTCS_T926_K12C3_N4)
**Hạng mục:** Sprint 4 – S4-09 AC4: *"Kịch bản nghiệm thu được chạy thử ít nhất một lần trên môi trường staging và ghi lại kết quả"*
**Thời gian thực hiện:** …/…/2026, …:… – …:… (GMT+7)
**Người thực hiện:** …………………………
**Môi trường:** Staging Docker Compose trên máy nhóm (S4-10) – `http://<IP máy staging>:8088`, 5 dịch vụ `db`, `backend`, `frontend`, `backup`, `mailpit`; `DEMO_MODE=true`; mã nguồn nhánh `develop` commit `………`
**Email:** xem trong Mailpit của máy staging (`http://localhost:8025`, máy đổi cổng thì theo `MAIL_UI_PORT`)

> Biên bản trước (16/10/2026) chạy trên máy local khi chưa có staging, không thay thế được AC4. Biên bản này ghi kết quả chạy thật trên staging.

---

## 1. Chuẩn bị
| # | Việc | Đã làm |
|---|---|---|
| 1 | `git pull origin develop` và `docker compose up -d --build`, `docker compose ps` đủ 5 dịch vụ `Up` | ☐ |
| 2 | Chủ homestay → **Dữ liệu mẫu** → **Nạp lại dữ liệu mẫu**, ghi thời gian chạy | ☐ |
| 3 | Chủ homestay → **Tham số**: nhập địa chỉ và số điện thoại homestay (hoá đơn bắt buộc) | ☐ |
| 4 | Chạy sau 12:00 để thử được phụ thu trả muộn (bước TC-09) | ☐ |

Dữ liệu mẫu dùng trong kịch bản (sinh theo ngày nạp): `BK-S4-016` đang ở, trả phòng hôm nay · `BK-S4-017` đang ở · `BK-S4-018`, `BK-S4-019` nhận phòng hôm nay, đã chốt phòng · `BK-S4-001`…`BK-S4-010` đã trả phòng, có quyết toán.

---

## 2. Kết quả từng bước
Mỗi dòng: thao tác đúng như cột "Bước", ghi kết quả thực tế quan sát được (kèm mã booking/số tiền), thời điểm, và Đạt/Không đạt.

| Mã | Bước | Vai trò | Kết quả mong đợi | Kết quả thực tế | Thời điểm | Đạt? |
|---|---|---|---|---|---|---|
| TC-01 | Nạp lại dữ liệu mẫu | Chủ homestay | Thông báo 4 loại phòng, 20 phòng, 30 booking; thời gian < 2 phút (S4-09 AC1, AC2) | | | |
| TC-02 | Mở trang khách trên điện thoại (360px), xem loại phòng, tra phòng trống 2 đêm tới, xem giá tạm tính từng đêm | Khách | Danh sách loại phòng có ảnh/giá; kết quả tra < 2 giây; giá từng đêm có nhãn ngày thường/cuối tuần/lễ (S2-03…S2-06) | | | |
| TC-03 | Đặt phòng với email thật của người chạy thử hoặc `khach.test@example.com` | Khách | Có mã booking 8 ký tự, trạng thái chờ xác nhận; Mailpit có email **tiếp nhận đặt phòng** kèm liên kết tra cứu (S2-07, S4-04) | | | |
| TC-04 | Bấm liên kết trong email, nhập email để tra cứu | Khách | Trang tra cứu điền sẵn mã; xem được trạng thái, ngày, số đêm, tổng tiền (S2-08) | | | |
| TC-05 | Xác nhận booking ở TC-03, ghi cọc chuyển khoản | Lễ tân | Booking đã xác nhận; Mailpit có email **xác nhận** có số đêm, giờ nhận/trả (S3-01, S4-04) | | | |
| TC-06 | Gán phòng cho booking ở TC-03; mở **Sơ đồ phòng** | Lễ tân | Chỉ gợi ý phòng đúng loại còn trống; sơ đồ hiện booking (S3-03, S3-10) | | | |
| TC-07 | Nhận phòng `BK-S4-018`, nhập CCCD 12 số | Lễ tân | Booking đang ở, phòng đang ở; chi tiết booking hiện giấy tờ `********xxxx`, bấm "Xem đầy đủ" hiện đủ số (S3-07, bảo vệ dữ liệu cá nhân) | | | |
| TC-08 | Thêm phụ thu thêm giường cho `BK-S4-017` | Lễ tân | Dòng phụ thu hiện trong chi tiết và bảng tính trả phòng (S4-02) | | | |
| TC-09 | Trả phòng `BK-S4-016` (sau 12:00), thu phần còn lại bằng tiền mặt | Lễ tân | Bảng tính có tiền phòng, **phụ thu trả muộn**, trừ cọc; booking đã trả phòng, phòng trống bẩn (S4-01) | | | |
| TC-10 | Xuất hoá đơn PDF `BK-S4-016` | Lễ tân | Số hoá đơn `năm-số thứ tự`, có dòng bằng chữ, in vừa A4 (S4-03) | | | |
| TC-11 | Dọn phòng vừa trả ở TC-09 trên điện thoại; gõ thẳng đường dẫn `/users` | Buồng phòng | Phòng về trống sạch; đường dẫn quản trị bị chặn (S3-09, S1-04, S4-08) | | | |
| TC-12 | Xem doanh thu hôm nay và công suất 30 ngày, tải CSV | Chủ homestay | Doanh thu có `BK-S4-016` (tách tiền phòng/phụ thu); CSV mở đúng tiếng Việt trên Excel (S4-06, S4-07) | | | |
| TC-13 | Xem doanh thu cả tháng | Lễ tân | Chỉ xem được ngày hôm nay (S4-07 AC4) | | | |
| TC-14 | Huỷ một booking đã xác nhận còn xa ngày nhận | Lễ tân | Tỷ lệ/số tiền hoàn tự tính; Mailpit có email **huỷ** (S3-05, S4-05) | | | |
| TC-15 | Xem **Nhật ký**, **Email đã gửi**, **Ma trận quyền API** | Quản trị | Nhật ký có đăng nhập, xác nhận booking, xem giấy tờ khách; lịch sử email đủ các thư trên (S1-05, S4-04, S4-08) | | | |
| TC-16 | Nạp lại dữ liệu mẫu lần 2 rồi lặp TC-07, TC-09 | Chủ homestay, Lễ tân | Nạp lại không lỗi kể cả khi đã có hoá đơn; chạy lại được (tính tái lập) | | | |

---

## 3. Lỗi phát hiện
| Mã | Mô tả | Mức độ | Bước | Cách xử lý / người phụ trách | Trạng thái |
|---|---|---|---|---|---|
| | | | | | |

---

## 4. Kết luận
- Tổng số bước: 16 · Đạt: … · Không đạt: …
- Thời gian nạp dữ liệu mẫu: … giây · Thời gian chạy trọn kịch bản: … phút
- Kết luận: ……………………………………………

| Đại diện nhóm | Đại diện Product Owner |
|---|---|
| (ký, ghi rõ họ tên) | (ký, ghi rõ họ tên) |
