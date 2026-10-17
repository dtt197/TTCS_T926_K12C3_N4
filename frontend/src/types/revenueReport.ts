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
