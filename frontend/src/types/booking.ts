export type BookingStatus =
  | 'CHO_XAC_NHAN'
  | 'DA_XAC_NHAN'
  | 'DA_HUY'
  | 'DA_NHAN_PHONG'
  | 'DA_TRA_PHONG'

export type BookingListItem = {
  bookingCode: string
  guestName: string
  roomTypeName: string
  checkInDate: string
  checkOutDate: string
  totalAmount: number
  status: BookingStatus
}