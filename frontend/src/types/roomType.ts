import type { AmenitySummary } from './amenity'

/** S2-09: dữ liệu ảnh của loại phòng */
export type RoomTypeImage = {
  id: number
  roomTypeId: number
  imageUrl: string
  thumbnailUrl: string
  displayOrder: number
  isPrimary: boolean
  createdAt?: string
}

/** S1-06: dữ liệu loại phòng nhận từ API /api/room-types. S1-08: kèm tiện nghi. S2-09: kèm ảnh đại diện & danh sách ảnh. */
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
  avatarUrl?: string | null
  images?: RoomTypeImage[]
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