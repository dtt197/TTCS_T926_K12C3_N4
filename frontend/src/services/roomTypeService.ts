import { apiRequest } from './apiClient'
import type { RoomType, RoomTypeImage, RoomTypePayload } from '../types/roomType'

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

/** S2-09: Lấy danh sách ảnh của loại phòng. */
export function getRoomTypeImages(roomTypeId: number) {
  return apiRequest<RoomTypeImage[]>(`/api/room-types/${roomTypeId}/images`)
}

/** S2-09: Tải ảnh lên cho loại phòng (tối đa 5MB, JPG/PNG, tối đa 8 ảnh). */
export function uploadRoomTypeImage(roomTypeId: number, file: File) {
  const formData = new FormData()
  formData.append('file', file)

  return apiRequest<RoomTypeImage>(`/api/room-types/${roomTypeId}/images`, {
    method: 'POST',
    body: formData,
  })
}