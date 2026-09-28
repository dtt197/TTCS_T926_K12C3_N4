/** S1-08: tiện nghi nhận từ API /api/amenities. */
export type Amenity = {
  id: number
  code: string
  name: string
  icon: string
  active: boolean
  roomTypeCount: number
}

/** Tiện nghi rút gọn đi kèm loại phòng (chỉ tiện nghi đang dùng). */
export type AmenitySummary = {
  id: number
  code: string
  name: string
  icon: string
}

export type AmenityPayload = {
  code: string
  name: string
  icon: string
}