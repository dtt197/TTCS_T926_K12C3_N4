import { apiRequest } from './apiClient'
import type { RoomStatus } from '../types/room'

export type ManagedRoom = {
  id: number
  roomNumber: string
  floor: number
  roomType: string
  status: RoomStatus
  active: boolean
  maintenanceReason: string | null
  maintenanceStartDate: string | null
  maintenanceEndDate: string | null
}

export type RoomSearchResponse = {
  rooms: ManagedRoom[]
  appliedRoomType: string | null
  appliedFloor: number | null
  appliedStatus: RoomStatus | null
  hasResult: boolean
}

export type AffectedBooking = {
  bookingCode?: string | null
  bookingNumber?: string | null
  guestName?: string | null
  checkInDate?: string | null
  checkOutDate?: string | null
}

export type RoomUpdateResponse = {
  room: ManagedRoom
  note: string
  bookingCheckAvailable: boolean
  warningRequired: boolean
  affectedFutureBookings: number
  warningMessage: string
  affectedBookings?: AffectedBooking[]
}

export type CreateRoomRequest = {
  roomNumber: string
  floor: number
  roomType: string
  status?: RoomStatus
}

export type UpdateRoomRequest = {
  roomNumber: string
  floor: number
  roomType: string
  note: string
  active: boolean
  status?: RoomStatus
  confirmWhenBookingCheckUnavailable: boolean
}

export async function createManagedRoom(request: CreateRoomRequest) {
  return apiRequest<ManagedRoom>('/api/room-management/rooms', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
  })
}

export async function searchManagedRooms(filters: {
  roomType?: string
  floor?: number
  status?: RoomStatus
}) {
  const params = new URLSearchParams()
  if (filters.roomType?.trim()) params.set('roomType', filters.roomType.trim())
  if (filters.floor !== undefined) params.set('floor', String(filters.floor))
  if (filters.status) params.set('status', filters.status)

  const query = params.toString()
  return apiRequest<RoomSearchResponse>(
    `/api/room-management/rooms${query ? `?${query}` : ''}`,
  )
}

export async function getManagedRoomNote(roomId: number) {
  const result = await apiRequest<{ note: string }>(
    `/api/room-management/rooms/${roomId}/note`,
  )
  return result.note
}

export async function updateManagedRoom(
  roomId: number,
  request: UpdateRoomRequest,
) {
  return apiRequest<RoomUpdateResponse>(
    `/api/room-management/rooms/${roomId}`,
    {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request),
    },
  )
}
