# Biên bản phục hồi thử (S4-10 Lát 4 – AC4)

| Mục | Nội dung |
|---|---|
| Ngày chạy | 18/10/2026 |
| Người thực hiện | (tên bạn) |
| Máy / môi trường | Docker Compose staging trên máy nhóm (PostgreSQL 15) |
| Tệp sao lưu dùng | `homestay_20261018_134453.dump` (bản mới nhất) |
| Dung lượng tệp | 148 KB |
| Bắt đầu phục hồi lúc | 13:47:08 |
| Hệ thống chạy lại lúc (theo script) | 13:47:33 |
| Đăng nhập lại được | Ngay sau khi script báo XONG (đăng nhập kiểm tra lúc 13:50) |
| **Thời gian phục hồi thực tế** | **25 giây** |
| Mức PO chấp nhận | 15 phút → **Đạt** |

## Đối chiếu số dòng
| Bảng | Trước khi phục hồi | Sau khi phục hồi | Ghi chú |
|---|---|---|---|
| Tài khoản (users) | 4 | 4 | Khớp |
| Loại phòng (room_types) | 4 | 4 | Khớp |
| Phòng (rooms) | 20 | 20 | Khớp |
| Booking (bookings) | 34 | 33 | Trước nhiều hơn 1 do booking 4H6MY8DX tạo sau thời điểm sao lưu |
| Hoá đơn (issued_invoices) | 0 | 0 | Khớp |

## Kiểm tra cụ thể
| Booking | Tạo lúc | So với lúc sao lưu (13:44:53) | Sau phục hồi |
|---|---|---|---|
| HY3JM4FV | 13:44:20 | Trước | Còn đầy đủ thông tin |
| 4H6MY8DX | 13:46:52 | Sau | Không còn (đúng như mong đợi; vẫn có trong `homestay_db_truoc_phuc_hoi`) |

- [x] Booking tạo trước thời điểm sao lưu (HY3JM4FV) có lại đầy đủ.
- [x] Booking tạo sau thời điểm sao lưu (4H6MY8DX) không còn; tra cứu báo không tìm thấy.
- [x] Đăng nhập được bằng tài khoản mẫu sau khi phục hồi.
- [x] Phục hồi từ một bản cũ hơn (`homestay_20261018_123832.dump`, chế độ kiểm tra) thành công: 32 booking, đúng dữ liệu tại 12:38.
- [x] Phục hồi từ tệp hỏng → `THAT_BAI`, CSDL đang chạy giữ nguyên (33 booking), backend tự bật lại.
- [x] Kiểm tra trên CSDL riêng (`--chi-kiem-tra`) không ảnh hưởng hệ thống đang chạy.

## Kết luận
Bản sao lưu tự động dựng lại được toàn bộ cơ sở dữ liệu. Thời gian phục hồi thực tế 25 giây, trong mức 15 phút PO chấp nhận. Nhật ký chi tiết: `backups/restore.log`.