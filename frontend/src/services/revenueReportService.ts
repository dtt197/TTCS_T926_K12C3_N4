import { apiRequest } from './apiClient'
import type { RevenueReport } from '../types/revenueReport'
import type { RevenueByRoomType } from '../types/revenueReport'

/** S4-07 Lát 1: thống kê doanh thu của các booking đã đóng trong khoảng ngày. */
export function getRevenueReport(
  startDate: string,
  endDate: string,
): Promise<RevenueReport> {
  const query = new URLSearchParams({ startDate, endDate })

  return apiRequest<RevenueReport>(`/api/reports/revenue?${query.toString()}`)
}

/**
 * S4-07 Lát 2:
 * Lấy doanh thu theo loại phòng trong khoảng ngày.
 * Không truyền roomTypeId để lấy tất cả loại phòng.
 */
export function getRevenueByRoomType(
  startDate: string,
  endDate: string,
  roomTypeId?: number,
): Promise<RevenueByRoomType[]> {
  const params = new URLSearchParams({
    startDate,
    endDate,
  })

  if (roomTypeId !== undefined) {
    params.set('roomTypeId', String(roomTypeId))
  }

  return apiRequest<RevenueByRoomType[]>(
    `/api/reports/revenue/by-room-type?${params.toString()}`,
  )
}
