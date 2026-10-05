/** S2-07: loại phòng khách chọn được trên trang đặt phòng công khai. */
export type PublicRoomTypeOption = {
  id: number
  code: string
  name: string
  standardCapacity: number
  maxCapacity: number
}

export type PublicRoomTypeDetails = PublicRoomTypeOption & {
  numberOfBeds: number
  description: string | null
  weekdayPrice: number | null
  weekendPrice: number | null
  images: string[]
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

/** S2-06: một đêm trong bảng tạm tính. label là "Ngày thường", "Cuối tuần" hoặc tên đợt lễ. */
export type QuoteNight = {
  date: string
  priceType: 'OVERRIDE' | 'WEEKEND' | 'WEEKDAY'
  label: string
  price: number
}

export type GuestQuoteAlternative = {
  roomTypeId: number
  roomTypeName: string
  maxCapacity: number
  totalAmount: number
}

/** S2-06: bảng giá tạm tính từng đêm cho khoảng ngày khách chọn. */
export type GuestQuote = {
  roomTypeId: number
  roomTypeName: string
  checkIn: string
  checkOut: string
  nights: number
  nightlyPrices: QuoteNight[]
  nightsTotal: number
  guestCount: number
  standardCapacity: number
  maxCapacity: number
  overCapacity: boolean
  minimumRooms: number
  alternatives: GuestQuoteAlternative[]
  extraGuests: number
  extraPersonFee: number
  surchargeAmount: number
  /** Tổng tiền phòng các đêm cộng phụ thu thêm người. */
  totalAmount: number
}