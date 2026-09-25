# Room status API

## `GET /api/rooms`

Trả về danh sách phòng đang hoạt động:

```json
[
  {
    "id": 1,
    "roomNumber": "101",
    "floor": 1,
    "roomType": "Phòng đôi",
    "status": "TRONG_SACH",
    "active": true
  }
]
```

## `PATCH /api/rooms/{roomId}/status`

Request:

```json
{
  "status": "TRONG_BAN"
}
```

Các giá trị status hợp lệ: `TRONG_SACH`, `TRONG_BAN`, `DANG_O`, `BAO_TRI`.

Không dùng endpoint này để chuyển sang `BAO_TRI`. Hãy dùng endpoint bảo trì bên dưới để bắt buộc nhập đủ thông tin.

## `PATCH /api/rooms/{roomId}/maintenance`

Request:

```json
{
  "reason": "Thay điều hòa",
  "startDate": "2026-09-25",
  "endDate": "2026-09-30"
}
```

Quy tắc:

- `reason` bắt buộc, không được chỉ chứa khoảng trắng.
- `startDate` bắt buộc.
- `endDate` bắt buộc.
- `endDate` phải lớn hơn hoặc bằng `startDate`.
- Phòng `DANG_O` không được chuyển thẳng sang `BAO_TRI`.

Nếu dữ liệu không hợp lệ, backend trả `409 Conflict` với thông báo tiếng Việt.

Sau khi thành công, `GET /api/rooms` trả thêm:

```json
{
  "maintenanceReason": "Thay điều hòa",
  "maintenanceStartDate": "2026-09-25",
  "maintenanceEndDate": "2026-09-30"
}
```

## `POST /api/rooms/{roomId}/check-in`

Request:

```json
{
  "guestName": "Nguyễn Minh Anh"
}
```

Nếu phòng không ở trạng thái `TRONG_SACH`, backend trả `409 Conflict`:

```json
{
  "message": "Không thể nhận phòng 102 vì phòng đang ở trạng thái Trống bẩn. Chỉ phòng Trống sạch mới được gán khi nhận phòng.",
  "timestamp": "2026-09-25T20:39:04+07:00"
}
```

Khi nhận phòng thành công, backend lưu bản ghi `check_ins` và chuyển phòng sang `DANG_O` trong cùng transaction.