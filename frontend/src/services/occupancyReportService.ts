import { apiRequest } from './apiClient'

export type DailyOccupancy = { date: string; soldNights: number; availableNights: number; occupancyPercent: number | null }
export type RoomTypeOccupancy = { roomTypeId: number; code: string; name: string; active: boolean; soldNights: number; availableNights: number; averageOccupancyPercent: number | null }

/** Detailed report payload returned by GET /api/reports/occupancy/detailed. */
export type DetailedOccupancyReport = {
  startDate: string
  endDate: string
  selectedRoomTypeId: number | null
  selectedRoomTypeName: string
  totalSoldNights: number
  totalAvailableNights: number
  totalAverageOccupancyPercent: number | null
  roomTypes: RoomTypeOccupancy[]
  dailyOccupancies: DailyOccupancy[]
}

export function getDetailedOccupancyReport(startDate: string, endDate: string, roomTypeId = '') {
  const params = new URLSearchParams({ startDate, endDate })
  if (roomTypeId) params.set('roomTypeId', roomTypeId)
  return apiRequest<DetailedOccupancyReport>(`/api/reports/occupancy/detailed?${params}`)
}
