/** S2-02: đợt giá đè nhận từ API /api/price-overrides. Ngày dạng yyyy-MM-dd. */
export type PriceOverride = {
  id: number
  name: string
  roomTypeId: number
  roomTypeCode: string
  roomTypeName: string
  startDate: string
  endDate: string
  nights: number
  pricePerNight: number
  createdByName: string
  updatedAt: string
}

export type PriceOverridePayload = {
  name: string
  roomTypeId: number
  startDate: string
  endDate: string
  pricePerNight: number
}