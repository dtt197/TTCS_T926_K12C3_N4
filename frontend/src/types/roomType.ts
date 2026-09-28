/** S1-06: dữ liệu loại phòng nhận từ API /api/room-types. */
export type RoomType = {
  id: number
  code: string
  name: string
  standardCapacity: number
  maxCapacity: number
  numberOfBeds: number
  description: string | null
  active: boolean
  roomCount: number
}

export type RoomTypePayload = {
  code: string
  name: string
  standardCapacity: number
  maxCapacity: number
  numberOfBeds: number
  description: string
}