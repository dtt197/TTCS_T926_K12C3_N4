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

## `POST /api/rooms/{roomId}/check-out`

Thực hiện trả phòng cho phòng đang ở (`DANG_O`).
Khi trả phòng thành công:
- Chuyển trạng thái phòng sang `TRONG_BAN` (Trống bẩn).
- Xóa các thông tin bảo trì liên quan.
- Lưu bản ghi lịch sử trạng thái: `DANG_O` → `TRONG_BAN`.

Nếu phòng không ở trạng thái `DANG_O`, backend trả `409 Conflict`:

```json
{
  "message": "Không thể trả phòng 101 vì phòng không ở trạng thái Đang ở."
}
```

## Quy tắc chuyển sang Bảo trì (`BAO_TRI`)

- Chặn chuyển trực tiếp phòng `DANG_O` (Đang ở) sang `BAO_TRI` (Bảo trì).
- Yêu cầu phải hoàn tất trả phòng (`check-out`) trước khi đưa phòng vào bảo trì.
- Khi bị từ chối, backend trả `409 Conflict`:
  `"Không thể đưa phòng 201 đang ở vào bảo trì. Hãy trả phòng trước."`
- Giữ nguyên trạng thái phòng và không ghi lịch sử khi thao tác bị từ chối.
- Sau khi trả phòng (phòng chuyển sang `TRONG_BAN`), được phép chuyển phòng sang `BAO_TRI` với thông tin bắt buộc: lý do bảo trì, ngày bắt đầu, ngày kết thúc.