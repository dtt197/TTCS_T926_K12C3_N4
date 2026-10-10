export type RoomShortageAlert = {
  date: string
  roomTypeCode: string
  roomTypeName: string
  bookingCount: number
  availableRooms: number
}

/**
 * S3-08: Một dòng booking trong danh sách liên quan đến cảnh báo thiếu phòng.
 * Booking tạo sau cùng lên đầu để ưu tiên xử lý.
 */
export type ShortageBooking = {
  id: number
  bookingCode: string
  guestName: string
  guestPhone: string | null
  checkInDate: string
  checkOutDate: string
  status: string
  holdExpired: boolean
  source: string | null
  createdAt: string
}

