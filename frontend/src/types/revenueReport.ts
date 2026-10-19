/** S4-07 Lát 1: thống kê doanh thu booking đã đóng. Ngày dạng yyyy-MM-dd, tiền tính bằng đồng. */
export type RevenueReport = {
  startDate: string
  endDate: string
  closedBookingCount: number
  totalNights: number
  roomRevenue: number
  surchargeRevenue: number
  totalRevenue: number
  /** null khi không có đêm nào trong khoảng ngày. */
  averageNightlyRate: number | null
}

/** S4-07 Lát 2: doanh thu thống kê riêng theo loại phòng. */
export type RevenueByRoomType = {
  roomTypeId: number
  roomTypeCode: string
  roomTypeName: string
  closedBookingCount: number
  totalNights: number
  roomRevenue: number
  surchargeRevenue: number
  totalRevenue: number
  averageNightlyRate: number | null
}

/** S4-07 Lát 3: So sánh tổng doanh thu với kỳ liền trước. */
export interface RevenueComparison {
  currentStartDate: string
  currentEndDate: string
  previousStartDate: string
  previousEndDate: string
  currentRevenue: number
  previousRevenue: number
  differenceAmount: number
  changePercent: number | null
  changeDirection: 'INCREASE' | 'DECREASE' | 'UNCHANGED' | 'NO_BASELINE'
  message: string
}

/** S4-07 Lát 3: So sánh doanh thu theo từng loại phòng. */
export interface RevenueByRoomTypeComparison {
  roomTypeId: number
  roomTypeCode: string
  roomTypeName: string
  currentRevenue: number
  previousRevenue: number
  differenceAmount: number
  changePercent: number | null
  changeDirection: 'INCREASE' | 'DECREASE' | 'UNCHANGED' | 'NO_BASELINE'
  message: string
}

export type RevenueByDay = {
  date: string
  totalRevenue: number
}
