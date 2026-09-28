import { apiRequest } from './apiClient'
import type { Amenity, AmenityPayload } from '../types/amenity'

/** S1-08: gọi API tiện nghi. apiRequest tự gắn token đăng nhập. */
const JSON_HEADERS = { 'Content-Type': 'application/json' }

export function getAmenities() {
  return apiRequest<Amenity[]>('/api/amenities')
}

export function createAmenity(payload: AmenityPayload) {
  return apiRequest<Amenity>('/api/amenities', {
    method: 'POST',
    headers: JSON_HEADERS,
    body: JSON.stringify(payload),
  })
}

export function updateAmenity(id: number, payload: AmenityPayload) {
  return apiRequest<Amenity>(`/api/amenities/${id}`, {
    method: 'PUT',
    headers: JSON_HEADERS,
    body: JSON.stringify(payload),
  })
}

export function updateAmenityStatus(id: number, active: boolean) {
  return apiRequest<Amenity>(`/api/amenities/${id}/status`, {
    method: 'PATCH',
    headers: JSON_HEADERS,
    body: JSON.stringify({ active }),
  })
}

export function deleteAmenity(id: number) {
  return apiRequest<void>(`/api/amenities/${id}`, { method: 'DELETE' })
}