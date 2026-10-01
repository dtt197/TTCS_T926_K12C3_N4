import { apiRequest } from './apiClient'
import type { GuestBookingPayload, GuestBookingResult, PublicRoomTypeOption } from '../types/guestBooking'

/** S2-07: API công khai, khách không cần đăng nhập. */
export function getPublicRoomTypes() {
  return apiRequest<PublicRoomTypeOption[]>('/api/public/room-types')
}

export function createGuestBooking(payload: GuestBookingPayload) {
  return apiRequest<GuestBookingResult>('/api/public/bookings', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}