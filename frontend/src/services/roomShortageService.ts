import { apiRequest } from './apiClient'
import type { RoomShortageAlert } from '../types/roomShortage'

/**
 * Task #s3-08: Lấy danh sách các cặp (ngày, loại phòng) có số booking còn hiệu lực vượt số phòng khả dụng.
 */
export async function getRoomShortageAlerts(): Promise<RoomShortageAlert[]> {
  return apiRequest<RoomShortageAlert[]>('/api/bookings/shortage-alerts')
}

