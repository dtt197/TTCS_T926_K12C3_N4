import { apiRequest } from './apiClient'
import type {
  BookingListItem,
  BookingStatus,
  PageResponse,
  BookingChangePreview,
  BookingAuditLog,
  AssignableRoom,
  BookingRoomChangeHistory,
  CheckInOption,
} from '../types/booking'

export type BookingPage = PageResponse<BookingListItem>

export type BookingUpdateRequest = {
  roomTypeId: number
  checkInDate: string
  checkOutDate: string
}

export type BookingFilters = {
  status: BookingStatus | ''
  checkInFrom: string
  checkInTo: string
  keyword: string
}

export async function searchBookings(filters: {
  keyword?: string
  status?: BookingStatus | ''
  checkInFrom?: string
  checkInTo?: string
  page?: number
  size?: number
}): Promise<PageResponse<BookingListItem>> {
  const params = new URLSearchParams()

  if (filters.keyword?.trim()) {
    params.set('keyword', filters.keyword.trim())
  }

  if (filters.status) {
    params.set('status', filters.status)
  }

  if (filters.checkInFrom) {
    params.set('checkInFrom', filters.checkInFrom)
  }

  if (filters.checkInTo) {
    params.set('checkInTo', filters.checkInTo)
  }

  params.set('page', String(filters.page ?? 0))
  if (filters.size !== undefined) {
    params.set('size', String(filters.size))
  }

  return apiRequest<PageResponse<BookingListItem>>(
    `/api/bookings?${params.toString()}`,
  )
}

export function getCheckInOptions(): Promise<CheckInOption[]> {
  return apiRequest<CheckInOption[]>('/api/bookings/check-in-options')
}

/** S2-10: booking mới nhất trước, 20 dòng mỗi trang (trang đầu tiên là 0). */
export function getLatestBookings(
  page = 0,
  filters?: Partial<BookingFilters>,
): Promise<PageResponse<BookingListItem>> {
  return searchBookings({
    page,
    status: filters?.status,
    checkInFrom: filters?.checkInFrom,
    checkInTo: filters?.checkInTo,
    keyword: filters?.keyword,
  })
}

export function updateBooking(
  id: number,
  request: BookingUpdateRequest,
): Promise<BookingListItem> {
  return apiRequest<BookingListItem>(`/api/bookings/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
}

export type BookingConfirmRequest = {
  amount: number
  paymentMethod: 'CASH' | 'BANK_TRANSFER'
  receivedDate: string
  paymentReference: string | null
}

export type BookingDepositResponse = {
  id: number
  amount: number
  paymentMethod: string
  receivedDate: string
  paymentReference: string | null
  reservationCode: string | null
  createdBy: string | null
  createdAt: string
}

export type BookingConfirmResponse = {
  id: number
  bookingCode: string
  status: string
  holdExpiresAt: string | null
  deposit: BookingDepositResponse
}

export function confirmBooking(id: number, request: BookingConfirmRequest): Promise<BookingConfirmResponse> {
  return apiRequest<BookingConfirmResponse>(`/api/bookings/${id}/confirm`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
}

/** S3-02 Lát 3: lễ tân huỷ booking kèm lý do. */
export function cancelBooking(id: number, reason: string): Promise<unknown> {
  return apiRequest<unknown>(`/api/bookings/${id}/cancel`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ reason }),
  })
}


export type BookingDetailResponse = {
  id: number
  bookingCode: string
  status: string
  holdExpiresAt: string | null
  deposit: BookingDepositResponse | null
  history?: BookingAuditLog[]
  depositAdjustments: BookingDepositAdjustment[]
  currentDepositTotal: number | null
}

export type BookingDepositAdjustment = {
  id: number
  adjustmentType: 'TANG' | 'GIAM'
  amount: number
  reason: string
  createdBy: string | null
  createdAt: string
}

export type BookingDepositAdjustmentRequest = {
  type: 'TANG' | 'GIAM'
  amount: number
  reason: string
}

export type BookingDepositAdjustmentResult = BookingDepositAdjustment & {
  bookingId: number
  currentDepositTotal: number
}

export function getBookingDetails(id: number): Promise<BookingDetailResponse> {
  return apiRequest<BookingDetailResponse>(`/api/bookings/${id}/details`)
}

export function getBookingHistory(id: number): Promise<BookingAuditLog[]> {
  return apiRequest<BookingAuditLog[]>(`/api/bookings/${id}/history`)
}

export function createBookingDepositAdjustment(
  id: number,
  request: BookingDepositAdjustmentRequest,
): Promise<BookingDepositAdjustmentResult> {
  return apiRequest<BookingDepositAdjustmentResult>(`/api/bookings/${id}/deposit-adjustments`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
}

/** S3-04: xem trước tình trạng phòng trống và số tiền được tính lại. */
export function previewBookingChange(
  id: number,
  request: BookingUpdateRequest,
): Promise<BookingChangePreview> {
  return apiRequest<BookingChangePreview>(`/api/bookings/${id}/preview`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
}

export function getAvailableRooms(id: number): Promise<AssignableRoom[]> {
  return apiRequest<AssignableRoom[]>(`/api/bookings/${id}/available-rooms`)
}

export function assignBookingRoom(id: number, roomId: number): Promise<BookingListItem> {
  return apiRequest<BookingListItem>(`/api/bookings/${id}/room`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ roomId }),
  })
}

export function changeBookingRoom(id: number, roomId: number, reason: string): Promise<BookingListItem> {
  return apiRequest<BookingListItem>(`/api/bookings/${id}/room-change`, {
    method: 'PUT', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ roomId, reason }),
  })
}

export function getBookingRoomChangeHistory(id: number): Promise<BookingRoomChangeHistory[]> {
  return apiRequest<BookingRoomChangeHistory[]>(`/api/bookings/${id}/room-change-history`)
}
