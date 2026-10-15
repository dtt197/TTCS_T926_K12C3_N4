import { getAccessToken } from './tokenStore'
import { apiRequest } from './apiClient'
import type { DailyOccupancy, RoomTypeOccupancy } from './occupancyReportService'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export type OccupancyByRoomTypeReport = {
  startDate: string
  endDate: string
  selectedRoomTypeId: number | null
  totalSoldNights: number
  totalAvailableNights: number
  totalAverageOccupancyPercent: number | null
  roomTypes: RoomTypeOccupancy[]
  dailyOccupancies: DailyOccupancy[]
}

function query(startDate: string, endDate: string, roomTypeId: string) {
  const params = new URLSearchParams({ startDate, endDate })
  if (roomTypeId) params.set('roomTypeId', roomTypeId)
  return params.toString()
}

/** Summary by room type: GET /api/reports/occupancy. */
export function getOccupancyReport(startDate: string, endDate: string, roomTypeId = '') {
  return apiRequest<OccupancyByRoomTypeReport>(`/api/reports/occupancy?${query(startDate, endDate, roomTypeId)}`)
}

/** CSV export: GET /api/reports/occupancy.csv. */
export async function downloadOccupancyCsv(startDate: string, endDate: string, roomTypeId = '') {
  const token = getAccessToken()
  const response = await fetch(`${API_BASE_URL}/api/reports/occupancy.csv?${query(startDate, endDate, roomTypeId)}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}, credentials: 'include',
  })
  if (!response.ok) throw new Error('Không thể tải tệp CSV.')
  const blob = await response.blob()
  const link = document.createElement('a')
  link.href = URL.createObjectURL(blob)
  link.download = `bao-cao-cong-suat-${startDate}-${endDate}.csv`
  link.click()
  URL.revokeObjectURL(link.href)
}
