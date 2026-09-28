/** S1-09: tham số vận hành nhận từ API /api/settings. */
export type CancellationTier = {
  hoursBeforeCheckIn: number
  refundPercent: number
}

export type OperatingSettings = {
  id: number
  homestayName: string
  address: string | null
  phone: string | null
  email: string | null
  checkInTime: string
  checkOutTime: string
  lateCheckoutFeePerHour: number
  extraPersonFee: number
  cancellationTiers: CancellationTier[]
  updatedByName: string
  updatedAt: string
}

export type OperatingSettingsPayload = {
  homestayName: string
  address: string
  phone: string
  email: string
  checkInTime: string
  checkOutTime: string
  lateCheckoutFeePerHour: number
  extraPersonFee: number
  cancellationTiers: CancellationTier[]
}