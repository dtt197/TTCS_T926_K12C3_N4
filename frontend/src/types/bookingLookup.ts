/** S2-08: chi tiết booking khách xem được khi tra cứu. Ngày dạng yyyy-MM-dd. */
export type BookingLookupResult = {
  bookingCode: string
  status: string
  statusLabel: string
  roomTypeName: string
  checkInDate: string
  checkOutDate: string
  nights: number
  totalAmount: number
  depositAmount: number
    /** S2-08 Lát 3: giờ nhận/trả phòng dạng HH:mm:ss. */
  checkInTime: string
  checkOutTime: string
}