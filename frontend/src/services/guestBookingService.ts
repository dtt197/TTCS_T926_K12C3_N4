import { apiRequest } from './apiClient'
import type {
  GuestBookingPayload,
  GuestBookingResult,
  GuestQuote,
  PublicRoomTypeDetails,
  PublicRoomTypeOption,
} from '../types/guestBooking'
import type { PublicRoomTypeDetail } from '../types/roomDetail'

/** S2-07: API công khai, khách không cần đăng nhập. */
export function getPublicRoomTypes() {
  return apiRequest<PublicRoomTypeOption[]>('/api/public/room-types')
}

export function getPublicRoomType(roomTypeId: number) {
  return apiRequest<PublicRoomTypeDetails>(`/api/public/room-types/${roomTypeId}`)
}

/** S2-04: Danh mục loại phòng công khai cho khách xem. */
export function getPublicRoomTypesCatalog() {
  return apiRequest<PublicRoomTypeDetail[]>('/api/public/room-types-catalog')
}

/** S2-04: Chi tiết một loại phòng công khai cho khách xem (bộ ảnh, mô tả, tiện nghi, sức chứa, số phòng trống). */
export function getPublicRoomTypeDetail(id: number | string) {
  return apiRequest<PublicRoomTypeDetail>(`/api/public/room-types/${id}`)
}

export function createGuestBooking(payload: GuestBookingPayload) {
  return apiRequest<GuestBookingResult>('/api/public/bookings', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  })
}

export type WalkInBookingPayload = {
  roomTypeId: number
  checkInDate: string
  checkOutDate: string
  guestName: string
  phone: string
  email?: string
  guestCount: number
  note?: string
}

export function createWalkInBooking(payload: WalkInBookingPayload) {
  return apiRequest<GuestBookingResult>('/api/public/bookings/walk-in', {
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