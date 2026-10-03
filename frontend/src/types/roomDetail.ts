export type PublicAmenity = {
  id: number
  code: string
  name: string
  icon: string
}

/**
 * S2-04: Chi tiết một loại phòng cho khách xem trước khi đặt.
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
  amenities: PublicAmenity[]
  images: string[]
}
