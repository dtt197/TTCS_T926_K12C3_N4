import { apiRequest } from './apiClient'

/** Khách lưu trú kèm số giấy tờ đã che (chỉ 4 ký tự cuối). */
export type GuestDocument = {
  guestId: number
  fullName: string
  primary: boolean
  maskedIdentityNumber: string | null
  hasIdentityNumber: boolean
}

export type GuestIdentity = {
  guestId: number
  fullName: string
  identityNumber: string
}

export function getGuestDocuments(bookingId: number): Promise<GuestDocument[]> {
  return apiRequest<GuestDocument[]>(`/api/bookings/${bookingId}/guests`)
}

/** Xem số giấy tờ đầy đủ; máy chủ ghi nhật ký mỗi lần gọi. */
export function revealGuestIdentity(bookingId: number, guestId: number): Promise<GuestIdentity> {
  return apiRequest<GuestIdentity>(`/api/bookings/${bookingId}/guests/${guestId}/identity-view`, {
    method: 'POST',
  })
}
