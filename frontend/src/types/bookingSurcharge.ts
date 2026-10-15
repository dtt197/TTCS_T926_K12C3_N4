/** S4-02 Lát 1: dòng phụ thu của booking. */
export type SurchargeType = 'THEM_NGUOI' | 'THEM_GIUONG'

export type BookingSurcharge = {
  id: number
  type: SurchargeType
  quantity: number
  unitPrice: number
  nights: number
  amount: number
  note: string | null
  createdBy: string
  createdAt: string
  defaultUnitPrice: number
  priceOverridden: boolean
  priceOverrideReason: string | null
  priceOverriddenBy: string | null
}

export type BookingSurchargeList = {
  bookingId: number
  canAddSurcharge: boolean
  maxNights: number
  defaultExtraPersonPrice: number
  defaultExtraBedPrice: number
  items: BookingSurcharge[]
  totalAmount: number
  currentGuestCount: number | null
  maxCapacity: number | null
  remainingCapacity: number | null
}

export type BookingSurchargeRequest = {
  type: SurchargeType
  quantity: number
  nights: number
  note: string | null
  unitPrice: number | null
  priceReason: string | null
}