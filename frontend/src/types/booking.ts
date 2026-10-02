export type BookingStatus =
  | 'CHO_XAC_NHAN'
  | 'DA_XAC_NHAN'
  | 'DA_HUY'
  | 'DA_NHAN_PHONG'
  | 'DA_TRA_PHONG'

/** S2-10: một dòng trong danh sách booking (GET /api/bookings, có phân trang). */
export type BookingListItem = {
  bookingCode: string
  guestName: string
  roomTypeNameSnapshot: string
  checkInDate: string
  checkOutDate: string
  totalAmount: number
  status: BookingStatus
  holdExpired: boolean
}

/** S2-10: một trang kết quả, trang đầu tiên là 0. */
export type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}