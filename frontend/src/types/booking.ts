export type BookingStatus =
  | 'CHO_XAC_NHAN'
  | 'DA_XAC_NHAN'
  | 'DA_HUY'
  | 'DA_NHAN_PHONG'
  | 'DA_TRA_PHONG'
  | 'DA_HET_HAN'

/** S2-10: một dòng trong danh sách booking (GET /api/bookings, có phân trang). */
export type BookingListItem = {
  id: number
  bookingCode: string
  guestName: string
  guestPhone?: string
  roomTypeId: number | null
  roomTypeNameSnapshot: string
  checkInDate: string
  checkOutDate: string
  totalAmount: number
  status: BookingStatus
  holdExpired: boolean
  /** S3-02: số phòng booking đang giữ, null nếu chưa gán được phòng. */
  roomNumber?: string | null
}

/** S2-10: một trang kết quả, trang đầu tiên là 0. */
export type PageResponse<T> = {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type Booking = {
  id: string
  bookingCode: string
  guestName: string
  guestPhone: string
  roomType: string
  checkInDate: string
  checkOutDate: string
  totalAmount: number
  status: BookingStatus
  createdAt: string
  holdExpired?: boolean
}

export type BookingFilter = {
  keyword?: string
  status?: BookingStatus | ''
  checkInFrom?: string
  checkInTo?: string
  page?: number
  size?: number
}

export type NightlyPrice = {
  date: string
  priceType: 'WEEKDAY' | 'WEEKEND' | 'OVERRIDE'
  label?: string
  price: number
}

/** S3-04: kết quả xem trước kiểm tra phòng trống và tính lại tiền. */
export type BookingChangePreview = {
  bookingId: number
  roomTypeId: number
  roomTypeName: string
  checkInDate: string
  checkOutDate: string
  numberOfNights: number
  totalAmount: number
  availableRooms: number
  available: boolean
  nightlyPrices: NightlyPrice[]
}

export type AssignableRoom = {
  id: number
  roomNumber: string
  floor: number
  roomType: string
  status: string
}

/** SCRUM-96 (S3-04): Lịch sử thay đổi booking (Audit Log). */
export type BookingAuditLog = {
  id: number
  bookingId: number
  bookingCode: string
  oldCheckInDate: string
  newCheckInDate: string
  oldCheckOutDate: string
  newCheckOutDate: string
  oldRoomTypeId: number | null
  oldRoomTypeName: string
  newRoomTypeId: number | null
  newRoomTypeName: string
  oldTotalAmount: number
  newTotalAmount: number
  actorUserId: number | null
  actorName: string | null
  actorEmail: string | null
  createdAt: string
}

