import type { AmenitySummary } from './amenity'

/** S1-06: dữ liệu loại phòng nhận từ API /api/room-types. S1-08: kèm tiện nghi đang dùng. */
export type RoomType = {
  id: number
  code: string
  name: string
  standardCapacity: number
  maxCapacity: number
  numberOfBeds: number
  description: string | null
  weekdayPrice: number | null
  weekendPrice: number | null
  active: boolean
  roomCount: number
  amenities: AmenitySummary[]
}

export type RoomTypePayload = {
  code: string
  name: string
  standardCapacity: number
  maxCapacity: number
  numberOfBeds: number
  description: string
  amenityIds: number[]
  weekdayPrice: number
  weekendPrice: number
}