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

/** S2-02 Lát 4: dữ liệu đang nhập trong form (chưa lưu) gửi lên để xem trước giá. */
export type PriceOverridePreviewPayload = {
  roomTypeId: number
  startDate: string
  endDate: string
  pricePerNight: number | null
  excludeId: number | null
}

export type PriceType = 'OVERRIDE' | 'WEEKEND' | 'WEEKDAY'

/** Một đêm: giá hiện tại (không tính đợt đang nhập) và giá sau khi lưu. null = chưa khai báo / chưa nhập. */
export type PreviewNight = {
  date: string
  currentType: PriceType
  currentLabel: string
  currentPrice: number | null
  newPrice: number | null
}

export type PriceOverridePreview = {
  nights: number
  nightlyPrices: PreviewNight[]
  currentTotal: number | null
  newTotal: number | null
  conflict: string | null
}