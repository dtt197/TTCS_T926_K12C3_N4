import { apiRequest } from './apiClient'

export type DailyOccupancy = {
  date: string
  soldNights: number
  availableNights: number
  occupancyPercent: number | null
}

export type OccupancyReport = {
  startDate: string
  endDate: string
  totalSoldNights: number
  totalAvailableNights: number
  averageOccupancyPercent: number | null
  dailyOccupancies: DailyOccupancy[]
}

export function getOccupancyReport(startDate: string, endDate: string) {
  const params = new URLSearchParams({ startDate, endDate })
  return apiRequest<OccupancyReport>(`/api/reports/occupancy?${params}`)
}
