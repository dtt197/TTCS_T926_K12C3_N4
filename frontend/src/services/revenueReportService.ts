import { apiRequest } from './apiClient'
import type { RevenueReport } from '../types/revenueReport'

/** S4-07 Lát 1: thống kê doanh thu của các booking đã đóng trong khoảng ngày. */
export function getRevenueReport(
  startDate: string,
  endDate: string,
): Promise<RevenueReport> {
  const query = new URLSearchParams({ startDate, endDate })

  return apiRequest<RevenueReport>(`/api/reports/revenue?${query.toString()}`)
}