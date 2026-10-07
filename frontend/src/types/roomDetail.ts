export type PublicAmenity = {
  id: number
  code: string
  name: string
  icon: string
}

export type CancellationTierPolicy = {
  hoursBeforeCheckIn: number
  refundPercent: number
  feePercent: number
  timeLabel: string
  feeDescription: string
}

/**
 * S2-04: Chi tiết một loại phòng cho khách xem trước khi đặt.
 * Bao gồm đầy đủ thông tin nhận phòng, trả phòng, chính sách trẻ nhỏ,
 * mức phụ thu thêm người và bảng mốc phí phạt hủy phòng.
 */
export type PublicRoomTypeDetail = {
  id: number
  code: string
  name: string
  standardCapacity: number
  maxCapacity: number
  numberOfBeds: number
  description: string
  weekdayPrice: number
  weekendPrice: number
  availableRooms: number
  activeRoomCount: number
  amenities: PublicAmenity[]
  images: string[]
  // S2-04 Policy fields
  checkInTime: string
  checkOutTime: string
  allowChildren: boolean
  childPolicy: string
  extraGuestFee: number
  cancellationPolicy: string
  cancellationTiers: CancellationTierPolicy[]
}
