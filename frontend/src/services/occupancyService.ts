import { apiRequest } from './apiClient'

export type DailyOccupancy = { date: string; soldNights: number; availableNights: number; occupancyPercent: number | null }
export type RoomTypeOccupancy = { roomTypeId: number; code: string; name: string; active: boolean; soldNights: number; availableNights: number; averageOccupancyPercent: number | null }
export type OccupancyReport = {
  startDate: string; endDate: string; selectedRoomTypeId: number | null
  totalSoldNights: number; totalAvailableNights: number; totalAverageOccupancyPercent: number | null
  roomTypes: RoomTypeOccupancy[]; dailyOccupancies: DailyOccupancy[]
}

export function getOccupancyReport(startDate: string, endDate: string, roomTypeId: string) {
  const query = new URLSearchParams({ startDate, endDate })
  if (roomTypeId) query.set('roomTypeId', roomTypeId)
  return apiRequest<OccupancyReport>(`/api/reports/occupancy?${query}`)
}
