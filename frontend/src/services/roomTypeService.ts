import { apiRequest } from './apiClient'
import type { RoomType, RoomTypePayload } from '../types/roomType'

/** S1-06: gọi API loại phòng. apiRequest tự gắn token đăng nhập. */
const JSON_HEADERS = { 'Content-Type': 'application/json' }

export function getRoomTypes() {
  return apiRequest<RoomType[]>('/api/room-types')
}

export function createRoomType(payload: RoomTypePayload) {
  return apiRequest<RoomType>('/api/room-types', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify(payload),
  })
}

export function updateRoomType(id: number, payload: RoomTypePayload) {
  return apiRequest<RoomType>(`/api/room-types/${id}`, {
    method: 'PUT',
    headers: JSON_HEADERS,
    body: JSON.stringify(payload),
  })
}

export function updateRoomTypeStatus(id: number, active: boolean) {
  return apiRequest<RoomType>(`/api/room-types/${id}/status`, {
    method: 'PATCH',
    headers: JSON_HEADERS,
    body: JSON.stringify({ active }),
  })
}

export function deleteRoomType(id: number) {
  return apiRequest<void>(`/api/room-types/${id}`, { method: 'DELETE' })
}

/** S1-08 AC2: gắn tiện nghi cho loại phòng (gắn trùng bị máy chủ chặn). */
export function addRoomTypeAmenity(roomTypeId: number, amenityId: number) {
  return apiRequest<RoomType>(`/api/room-types/${roomTypeId}/amenities/${amenityId}`, { method: 'POST' })
}

/** S1-08: bỏ tiện nghi khỏi loại phòng. */
export function removeRoomTypeAmenity(roomTypeId: number, amenityId: number) {
  return apiRequest<RoomType>(`/api/room-types/${roomTypeId}/amenities/${amenityId}`, { method: 'DELETE' })
}