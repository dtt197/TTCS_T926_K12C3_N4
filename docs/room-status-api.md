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