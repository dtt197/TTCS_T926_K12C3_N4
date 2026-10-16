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
  CustomerNoShowResponse,
  NoShowUndoEligibility,
  AppointmentActionLog,
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
  checkedOutAt?: string | null
  earlyCheckoutReason?: string | null
  settlementTransactionStatus?: string | null
  registeredGuests?: { fullName: string; primary: boolean }[]
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

function invoiceFilenameFromDisposition(disposition: string | null, bookingCode: string): string {
  const encodedName = disposition?.match(/filename\*\s*=\s*UTF-8''([^;]+)/i)?.[1]
  const plainName = disposition?.match(/filename\s*=\s*"?([^";]+)"?/i)?.[1]
  const headerName = encodedName ? decodeURIComponent(encodedName.trim()) : plainName?.trim()
  return headerName || `invoice_${bookingCode}.pdf`
}

export async function downloadBookingInvoice(id: number, bookingCode: string): Promise<void> {
  const response = await apiRequest<{ blob: Blob; headers: Headers }>(`/api/bookings/${id}/invoice.pdf`, {
    responseType: 'blob',
  })
  const blob = response.blob.type === 'application/pdf' ? response.blob : new Blob([response.blob], { type: 'application/pdf' })
  const filename = invoiceFilenameFromDisposition(response.headers.get('Content-Disposition'), bookingCode)
  const objectUrl = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = objectUrl
  link.download = filename
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.setTimeout(() => URL.revokeObjectURL(objectUrl), 1000)
}

/** S3-10 Lát 3: thông tin booking để mở nhanh chi tiết từ sơ đồ phòng. */
export function getBookingSummary(id: number): Promise<BookingListItem> {
  return apiRequest<BookingListItem>(`/api/bookings/${id}/summary`)
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

/** S3-05 Lát 2: thông tin huỷ của một booking (chỉ có giá trị khi booking đã huỷ). */
export type BookingCancellationInfo = {
  status: string
  cancelReason: string | null
  cancelNote: string | null
  cancelDepositAmount: number | null
  cancelRefundPercent: number | null
  cancelRefundAmount: number | null
  cancelledBy: string | null
  cancelledAt: string | null
}

export function getBookingCancellationInfo(
  id: number,
): Promise<BookingCancellationInfo> {
  return apiRequest<BookingCancellationInfo>(
    `/api/bookings/${id}/cancellation-info`,
  )
}

export type NoShowEligibility = {
  bookingId: number
  bookingCode: string
  eligible: boolean
  reason: string
  scheduledCheckIn: string
  eligibleAfter: string
  evaluatedAt: string
  status: string
  checkedIn: boolean
}

export function checkBookingNoShowEligibility(id: number): Promise<NoShowEligibility> {
  return apiRequest<NoShowEligibility>(`/api/bookings/${id}/no-show/eligibility`)
}

export function markBookingNoShow(id: number): Promise<BookingListItem> {
  return apiRequest<BookingListItem>(`/api/bookings/${id}/no-show`, {
    method: 'POST',
  })
}

/** S4-04 (SCRUM-150): Tra cứu lịch sử khách bỏ hẹn theo số điện thoại */
export function getCustomerNoShowHistory(phone: string): Promise<CustomerNoShowResponse> {
  return apiRequest<CustomerNoShowResponse>(
    `/api/customers/no-show-history?phone=${encodeURIComponent(phone.trim())}`,
  )
}

/** S4-04 (SCRUM-151): Kiểm tra điều kiện hoàn tác thao tác không đến (15 phút) */
export function checkNoShowUndoEligibility(id: number): Promise<NoShowUndoEligibility> {
  return apiRequest<NoShowUndoEligibility>(`/api/bookings/${id}/no-show/undo-eligibility`)
}

/** S4-04 (SCRUM-151): Hoàn tác thao tác đánh dấu không đến */
export function undoBookingNoShow(id: number): Promise<BookingListItem> {
  return apiRequest<BookingListItem>(`/api/bookings/${id}/no-show/undo`, {
    method: 'POST',
  })
}

/** S4-04 (SCRUM-151): Xem lịch sử nhật ký thao tác lễ tân */
export function getAppointmentActionLogs(id: number): Promise<AppointmentActionLog[]> {
  return apiRequest<AppointmentActionLog[]>(`/api/bookings/${id}/action-logs`)
}


