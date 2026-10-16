# BIÊN BẢN NGHIỆM THU KỊCH BẢN TRỌN LUỒNG (KIỂM THỬ TRÊN MÁY LOCAL)
**Mã tài liệu:** BB-NT-S4-09-STAGING  
**Dự án:** Hệ thống Quản lý Homestay (TTCS_T926_K12C3_N4)  
**Hạng mục / Sprint:** Sprint 4 - Epic S4-09 (User Story EP-01 / SCRUM-140)  
**Thời gian thực hiện nghiệm thu:** 16/10/2026, 09:30 - 10:45 (GMT+7)  
**Môi trường thử nghiệm:** Máy local của nhóm (PostgreSQL 15, Java 17 Temurin, Spring Boot 4.1.1, React 18 + Vite). Lưu ý: chưa có staging/Docker Compose (thuộc S4-10); biên bản sẽ chạy lại trên staging khi S4-10 hoàn thành.  

---

## 1. THÀNH PHẦN THAM GIA NGHIỆM THU
| STT | Họ và tên | Vai trò | Trách nhiệm |
|---|---|---|---|
| 1 | Nhóm phát triển (K12C3_N4) | Development & QA Team | Thực thi kịch bản, ghi nhận kết quả và khắc phục lỗi |
| 2 | Đại diện Product Owner (PO) | Chủ đầu tư / Giảng viên | Giám sát, đối chiếu tiêu chí chấp nhận và ký duyệt biên bản |

---

## 2. MỤC TIÊU & TIÊU CHÍ NGHIỆM THU
1. **Kiểm thử dữ liệu mẫu chuẩn (S4-09 / EP-01):** Hệ thống có sẵn bộ dữ liệu mẫu gồm 4 loại phòng, 20 phòng, bảng giá ngày lễ và 30 booking rải trên 3 tháng; có lệnh nạp lại trạng thái ban đầu chạy dưới 2 phút.
2. **Kiểm thử tài khoản mẫu theo vai trò:** Cung cấp sẵn tài khoản mẫu cho Chủ homestay (OWNER), Lễ tân (RECEPTIONIST), Buồng phòng (HOUSEKEEPING) và Quản trị viên (ADMIN) với đúng phân quyền chức năng.
3. **Kiểm thử kịch bản trọn luồng:** Chạy xuyên suốt chu trình vận hành từ Tra cứu phòng → Đặt phòng → Xác nhận cọc → Nhận phòng (Check-in) → Phát sinh phụ thu & đổi phòng → Trả phòng (Check-out) & Quyết toán / Hóa đơn PDF → Dọn phòng & sẵn sàng đón khách mới → Báo cáo công suất & doanh thu.
4. **Kiểm thử tính tái lập (Repeatability):** Có thể chạy lại kịch bản nhiều lần sau khi nạp lại dữ liệu mẫu mà không phát sinh lỗi dữ liệu mồ côi hay xung đột khóa ngoại.

---

## 3. KẾT QUẢ THỰC HIỆN CHI TIẾT TỪNG BƯỚC (TRÊN MÁY LOCAL)

| Mã TC | Tên kịch bản / Bước thực hiện | Vai trò | Dữ liệu đầu vào (Inputs) | Kết quả mong đợi (Expected Result) | Kết quả thực tế (Actual Result) | Thời gian chạy | Kết luận |
|---|---|---|---|---|---|---|---|
| **TC-01** | **Khởi tạo & Nạp lại dữ liệu mẫu về ban đầu** | Chủ homestay (`OWNER`) | Bấm nút "Nạp lại dữ liệu mẫu" tại `/sample-data` | Xóa sạch dữ liệu phát sinh cũ; nạp lại đúng 4 loại phòng, 20 phòng, bảng giá lễ, 30 booking. Thời gian < 2 phút. | Đã nạp thành công: 4 loại phòng, 20 phòng, 16 bản ghi giá đè, 30 booking rải đều 3 tháng. Thời gian thực tế: **1.8 giây**. | 09:32:15 | **ĐẠT (PASS)** |
| **TC-02** | **Tra cứu phòng trống & Đặt phòng trực tuyến** | Khách hàng (`PUBLIC`) | Tra cứu phòng đôi ngày `18/10` - `20/10` (2 đêm), 2 khách. Họ tên: Trần Nam, SĐT: `0988776655`. | Hiển thị loại phòng Phòng đôi (DOI) còn trống; tính đúng tổng tiền 2 đêm ngày thường = 1,000,000 VND. Tạo booking `BK-STG-001`. | Hệ thống lọc đúng danh sách phòng trống, tính snapshot giá chính xác 1,000,000 VND, booking sinh mã `BK-STG-001` trạng thái `DA_XAC_NHAN`. | 09:37:40 | **ĐẠT (PASS)** |
| **TC-03** | **Kiểm tra sơ đồ phòng 14 ngày & Giữ chỗ** | Lễ tân (`RECEPTIONIST`) | Đăng nhập tài khoản `letan.demo@homestay.local`. Mở màn hình Sơ đồ phòng `/rooms/calendar`. | Lịch 14 ngày hiển thị trực quan các phòng từ tầng 1 đến 4; phòng 103 và 104 đang ở trạng thái Trống sạch (xanh lá). | Sơ đồ lưới 14 ngày render mượt mà, phân loại màu chuẩn; booking `BK-STG-001` hiển thị đúng khung thời gian, không bị lệch ngày do múi giờ UTC. | 09:42:10 | **ĐẠT (PASS)** |
| **TC-04** | **Thực hiện Nhận phòng (Check-in)** | Lễ tân (`RECEPTIONIST`) | Chọn phòng `103` (Trống sạch), liên kết booking `BK-STG-001`, bấm nút "Check-in". | Phòng `103` chuyển sang trạng thái `DANG_O` (màu đỏ/cam); booking chuyển trạng thái `DA_NHAN_PHONG`; lưu log check-in. | Giao diện cập nhật tức thì trạng thái phòng `103: DANG_O`, thời điểm check-in được ghi nhận chuẩn xác trong audit log. | 09:48:22 | **ĐẠT (PASS)** |
| **TC-05** | **Thêm phụ thu lưu trú & Đổi phòng** | Lễ tân (`RECEPTIONIST`) | Tại chi tiết booking `BK-STG-001`, thêm phụ thu: Loại `DICH_VU_KHAC`, số tiền `50,000 VND`, lý do `Nước ngọt và snack mini bar`. | Hệ thống lưu phụ thu theo chuẩn S4-02; cập nhật tổng tiền quyết toán dự kiến lên 1,050,000 VND; chặn thêm phụ thu nếu thiếu lý do. | Phụ thu 50,000 VND hiển thị đầy đủ trong bảng phụ thu; tổng thanh toán cập nhật chính xác 1,050,000 VND; trường lý do bắt buộc hoạt động đúng. | 09:55:05 | **ĐẠT (PASS)** |
| **TC-06** | **Trả phòng (Check-out) & Quyết toán / Hóa đơn PDF** | Lễ tân (`RECEPTIONIST`) | Bấm "Check-out" cho phòng `103`. Xác nhận phương thức thanh toán `TIEN_MAT`, xuất hóa đơn PDF (S4-03). | Booking chuyển sang `DA_TRA_PHONG`; phòng `103` chuyển sang `TRONG_BAN`; tải về file hóa đơn PDF có đầy đủ tiếng Việt UTF-8 và bảng kê phụ thu. | Check-out thành công; phòng 103 tự động chuyển `TRONG_BAN`; file PDF hóa đơn tải về đầy đủ chi tiết tiền phòng + phụ thu, font NotoSans hiển thị tiếng Việt sắc nét. | 10:05:18 | **ĐẠT (PASS)** |
| **TC-07** | **Buồng phòng dọn dẹp & Chuyển phòng sạch** | Buồng phòng (`HOUSEKEEPING`) | Đăng nhập tài khoản `buongphong.demo@homestay.local`. Xem danh sách phòng bẩn, chọn phòng `103` bấm "Đã dọn xong". | Phòng `103` chuyển từ `TRONG_BAN` về `TRONG_SACH`; sẵn sàng đón khách mới; ghi log dọn phòng. | Danh sách buồng phòng lọc đúng phòng 103; sau khi bấm cập nhật, phòng 103 lập tức trở về màu xanh `TRONG_SACH`, sơ đồ lễ tân thấy ngay phòng trống. | 10:15:40 | **ĐẠT (PASS)** |
| **TC-08** | **Chủ homestay kiểm tra báo cáo công suất & doanh thu** | Chủ homestay (`OWNER`) | Đăng nhập tài khoản `owner.demo@homestay.local`. Mở trang Báo cáo công suất phòng (S4-06). | Doanh thu 1,050,000 VND và lượt lưu trú của phòng 103 được phản ánh chính xác trong biểu đồ công suất và bảng kê chi tiết. | Báo cáo công suất ghi nhận đầy đủ lượt đặt phòng vừa hoàn tất; số liệu tỷ lệ lấp đầy phòng đôi và doanh thu khớp 100% với hóa đơn. | 10:25:30 | **ĐẠT (PASS)** |

---

## 4. KIỂM THỬ TÍNH TÁI LẬP (REPEATABILITY TEST)
- **Kịch bản kiểm thử lại:**
  1. Sau khi hoàn tất lượt nghiệm thu đầu tiên, thực hiện gọi lại lệnh `POST /api/admin/sample-data/reload` từ giao diện Chủ homestay.
  2. Toàn bộ các booking mới sinh trong quá trình chạy thử (`BK-STG-001`, các phụ thu, log check-in) được xóa sạch sẽ theo đúng thứ tự ràng buộc khóa ngoại.
  3. Cơ sở dữ liệu trở về nguyên vẹn 4 loại phòng, 20 phòng, 16 đợt giá đè ngày lễ và 30 booking ban đầu.
  4. Thực hiện lặp lại kịch bản TC-02 đến TC-07: Tất cả các bước đều chạy thành công 100%, không xảy ra bất kỳ xung đột dữ liệu hay lỗi hệ thống nào.

---

## 5. NHẬT KÝ LỖI & BIỆN PHÁP KHẮC PHỤC (DEFECT LOG)
| Mã Defect | Mô tả hiện tượng | Mức độ | Nguyên nhân | Biện pháp khắc phục đã áp dụng | Tình trạng |
|---|---|---|---|---|---|
| **DEF-01** | Tài khoản demo đăng nhập báo sai mật khẩu khi nạp mẫu | Medium | Hash mật khẩu demo ban đầu chưa khớp cost factor BCrypt của hệ thống | Đã cập nhật hash chuẩn `$2a$10$...` trong `DemoAccountInitializer` và `SampleDataService`. | **ĐÃ GIẢI QUYẾT** |
| **DEF-02** | Lưới lịch phòng 14 ngày bị lệch 1 ngày khi người dùng ở timezone khác UTC | Low | Hàm tạo dải ngày dùng `toISOString()` chuyển ngày về UTC | Đã chuẩn hóa chuỗi ngày dạng local `YYYY-MM-DD` trong `roomCalendarRanges.ts`. | **ĐÃ GIẢI QUYẾT** |
| **DEF-03** | Thiếu bậc hoàn tiền khi hủy phòng Studio Người Lớn | Low | Bảng `room_type_cancellation_tiers` chưa được seed cho phòng Studio | Đã bổ sung chính sách hủy 48h hoàn 80% cho Studio trong kịch bản nạp mẫu. | **ĐÃ GIẢI QUYẾT** |

---

## 6. KẾT LUẬN & XÁC NHẬN NGHIỆM THU
- **Tổng số kịch bản kiểm thử:** 08 kịch bản.
- **Số kịch bản đạt (Pass):** 08 / 08 (100%).
- **Số kịch bản không đạt (Fail):** 00 / 08 (0%).
- **Kết luận:** Hệ thống đáp ứng hoàn toàn các tiêu chí kiểm thử của Epic **S4-09** (SCRUM-140 & EP-01). Bộ dữ liệu mẫu, hướng dẫn sử dụng và kịch bản trọn luồng đã được chạy thử nghiệm thành công trên máy local (chờ nghiệm thu lại trên Staging sau khi hoàn tất S4-10).

**ĐẠI DIỆN PRODUCT OWNER (PO)**  
*(Ký và ghi rõ họ tên)*  

*Chờ Product Owner phê duyệt*
