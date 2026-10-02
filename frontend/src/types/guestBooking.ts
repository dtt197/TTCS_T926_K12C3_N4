/** S2-07: loại phòng khách chọn được trên trang đặt phòng công khai. */
export type PublicRoomTypeOption = {
  id: number
  code: string
  name: string
  maxCapacity: number
}

export type GuestBookingPayload = {
  roomTypeId: number
  checkInDate: string
  checkOutDate: string
  guestName: string
  phone: string
  email: string
  guestCount: number
  note: string
  acceptedCancellationPolicy: boolean
}

/** Kết quả sau khi gửi thành công. Ngày dạng yyyy-MM-dd, holdExpiresAt dạng ISO. */
export type GuestBookingResult = {
  bookingCode: string
  status: string
  statusLabel: string
  roomTypeName: string
  checkInDate: string
  checkOutDate: string
  nights: number
  totalAmount: number
  holdExpiresAt: string
}