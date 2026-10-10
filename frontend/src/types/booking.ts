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
  roomConfirmedAt?: string | null
  roomConfirmedBy?: string | null
}

export type CheckInOption = {
  bookingId: number
  bookingCode: string
  guestName: string
  roomId: number
  roomNumber: string
  checkInDate: string
  checkOutDate: string
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

export type BookingRoomChangeHistory = {
  id: number
  bookingId: number
  oldRoomId: number
  oldRoomNumber: string
  newRoomId: number
  newRoomNumber: string
  actorUserId: number
  actorName: string
  changedAt: string
  reason: string
}

/** S3-05: lý do huỷ booking, khớp với backend. */
export type CancelReason =
  | 'KHACH_DOI_KE_HOACH'
  | 'KHACH_KHONG_LIEN_LAC'
  | 'TRUNG_BOOKING'
  | 'LY_DO_KHAC'

/** S3-05: kết quả xem trước huỷ. appliedTierHours là null khi không thuộc mốc hoàn cọc nào. */
export type CancellationPreview = {
  bookingCode: string
  guestName: string
  roomTypeName: string
  status: BookingStatus
  checkInDate: string
  checkInTime: string
  hoursBeforeCheckIn: number
  depositAmount: number
  appliedTierHours: number | null
  refundPercent: number
  refundAmount: number
}

/** S3-05: dữ liệu gửi lên khi xác nhận huỷ. Không có số tiền hoàn: hệ thống tự tính. */
export type CancelBookingInput = {
  reason: CancelReason
  note?: string
}

/** S3-05: kết quả sau khi huỷ. */
export type BookingCancellationResult = {
  bookingCode: string
  status: BookingStatus
  cancelReason: CancelReason
  cancelNote: string | null
  depositAmount: number
  refundPercent: number
  refundAmount: number
}
