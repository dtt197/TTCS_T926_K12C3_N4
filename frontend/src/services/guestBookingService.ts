import { apiRequest } from './apiClient'
import type {
  GuestBookingPayload,
  GuestBookingResult,
  GuestQuote,
  PublicRoomTypeOption,
} from '../types/guestBooking'

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

/** S2-06: giá tạm tính từng đêm và phụ thu thêm người, khách không cần đăng nhập. Ngày dạng yyyy-MM-dd. */
export function getGuestQuote(roomTypeId: number, checkIn: string, checkOut: string, guestCount: number) {
  const query = new URLSearchParams({
    roomTypeId: String(roomTypeId),
    checkIn,
    checkOut,
    guestCount: String(guestCount),
  })
  return apiRequest<GuestQuote>(`/api/public/quote?${query.toString()}`)
}