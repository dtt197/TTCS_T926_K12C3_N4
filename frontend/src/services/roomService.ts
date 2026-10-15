import { getAccessToken } from './tokenStore'
import type { Room, RoomStatus, MaintenanceDraft, RoomStatusHistory } from '../types/room'
import type { BookingStatus } from '../types/booking'
import type { BookingSurcharge } from '../types/bookingSurcharge'

const API_BASE_URL = `${import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}/api`

type ApiError = {
  message?: string
}

export class RoomApiRequestError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.name = 'RoomApiRequestError'
    this.status = status
  }
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const token = getAccessToken()

  const headers = new Headers(options?.headers)

  headers.set('Content-Type', 'application/json')

  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  })

  const body: unknown = await response.json().catch(() => null)

  if (!response.ok) {
    const errorBody = body as ApiError | null

    throw new RoomApiRequestError(
      response.status,
      errorBody?.message ??
        `Yêu cầu thất bại (${response.status})`
    )
  }

  return body as T
}

export function getRooms() {
  return request<Room[]>('/rooms')
}

export function getRoomHistory(roomId: number) {
  return request<RoomStatusHistory[]>(`/rooms/${roomId}/history`)
}

export function updateRoomStatus(roomId: number, status: RoomStatus) {
  return request<Room>(`/rooms/${roomId}/status`, {
    method: 'PATCH',
   
    body: JSON.stringify({ status }),
  })
}

export type CheckInRequest = {
  primaryGuestName: string
  primaryGuestIdentityNumber: string
  accompanyingGuestNames: string[]
  earlyCheckInConfirmed: boolean
}

export type RegisteredGuest = {
  fullName: string
  primary: boolean
}

export function checkIn(roomId: number, bookingId: number, guestDetails: CheckInRequest) {
  return request<{
    id: number
    bookingId: number
    bookingCode: string
    bookingStatus: BookingStatus
    roomId: number
    roomNumber: string
    roomStatus: RoomStatus
    guestName: string
    checkedInAt: string
    registeredGuests: RegisteredGuest[]
  }>(
    `/rooms/${roomId}/check-in`,
    {
      method: 'POST',
      body: JSON.stringify({ bookingId, ...guestDetails }),
    },
  )
}
export function putRoomIntoMaintenance(
  roomId: number,
  maintenance: Omit<MaintenanceDraft, 'endDate'> & { endDate: string | null },
) {
  return request<Room>(`/rooms/${roomId}/maintenance`, {
    method: 'PATCH',
    
    body: JSON.stringify(maintenance),
  })
}

export type CheckoutNight = {
  date: string
  label: string
  roomPrice: number
  surchargeAmount: number
  total: number
}

export type CheckoutPreview = {
  bookingId: number
  bookingCode: string
  roomId: number
  roomNumber: string
  guestName: string
  checkInDate: string
  checkOutDate: string
  nightlyPrices: CheckoutNight[]
  nightlyBreakdownReliable: boolean
  warning: string | null
  roomTotal: number
  surchargeAmount: number
  surchargeBreakdownComplete: boolean
  extraGuestCount: number | null
  extraPersonFeeSnapshot: number | null
  surchargeNights: number | null
  roomTotalExcludingSurcharge: number | null
  surchargeBreakdownWarning: string | null
  bookingSurcharges: BookingSurcharge[]
  bookingSurchargeTotal: number
  depositTotal: number
  checkoutTimeSnapshot: string
  serverCheckoutTime: string
  operatingSettingsId: number
  lateCheckoutHours: number
  lateCheckoutFeePerHour: number | null
  lateCheckoutFee: number
  settlementTotalBeforeDeposit: number
  lateCheckoutPricingBasis: 'NONE' | 'HOURLY' | 'LAST_NIGHT'
  checkoutAllowed: boolean
  checkoutBlockedReason: string | null
  settlementType: 'COLLECT' | 'ZERO' | 'REFUND'
  amount: number
  previewToken: string
  earlyCheckout: boolean
  bookedNights: number
  usedNights: number
  unusedNights: number
  unusedRoomValue: number | null
  unusedRoomValueWarning: string | null
}

export function getCheckoutPreview(roomId: number) {
  return request<CheckoutPreview>(`/rooms/${roomId}/checkout-preview`)
}

export type CheckoutPaymentRequest = {
  previewToken: string
  paymentMethod?: 'CASH' | 'BANK_TRANSFER'
  paymentReference?: string
  amountReceivedConfirmed?: boolean
  refundCompletedConfirmed?: boolean
  earlyCheckoutReason?: string
}

export { buildCheckoutPaymentRequest } from './checkoutAc3'

export function checkOut(roomId: number, settlement: CheckoutPaymentRequest) {
  return request<Room>(`/rooms/${roomId}/check-out`, {
    method: 'POST',
    body: JSON.stringify(settlement),
  })
}

/** S3-09 AC4: Buồng phòng báo sự cố → phòng chuyển sang bảo trì */
export type IncidentSeverity = 'NHE' | 'NGHIEM_TRONG'

export function reportRoomIncident(
  roomId: number,
  severity: IncidentSeverity,
  description: string,
  idempotencyKey: string,
) {
  return request<Room>(`/rooms/${roomId}/incident`, {
    method: 'PATCH',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ severity, description }),
  })
}

export interface RoomAvailabilityResponse {
  roomTypeId: number;
  name: string;
  capacity: number;
  price: number;
  availableRooms: number;
}

export function searchAvailableRooms(checkIn: string, checkOut: string, guestCount: number) {
  const params = new URLSearchParams({
    checkIn,
    checkOut,
    guestCount: guestCount.toString(),
  });
  return request<RoomAvailabilityResponse[]>(`/public/rooms/search?${params.toString()}`);
}
