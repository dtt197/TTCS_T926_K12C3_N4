import { getAccessToken } from './tokenStore'
import { apiRequest } from './apiClient'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export type DailyOccupancy = { date: string; soldNights: number; availableNights: number; occupancyPercent: number | null }
export type RoomTypeOccupancy = { roomTypeId: number; code: string; name: string; active: boolean; soldNights: number; availableNights: number; averageOccupancyPercent: number | null }
export type OccupancyReport = { startDate: string; endDate: string; selectedRoomTypeId: number | null; selectedRoomTypeName: string; totalSoldNights: number; totalAvailableNights: number; totalAverageOccupancyPercent: number | null; roomTypes: RoomTypeOccupancy[]; dailyOccupancies: DailyOccupancy[] }

function query(startDate: string, endDate: string, roomTypeId: string) {
  const params = new URLSearchParams({ startDate, endDate })
  if (roomTypeId) params.set('roomTypeId', roomTypeId)
  return params.toString()
}

export function getOccupancyReport(startDate: string, endDate: string, roomTypeId: string) {
  return apiRequest<OccupancyReport>(`/api/reports/occupancy?${query(startDate, endDate, roomTypeId)}`)
}

export async function downloadOccupancyCsv(startDate: string, endDate: string, roomTypeId: string) {
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
