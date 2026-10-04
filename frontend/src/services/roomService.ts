import { getAccessToken } from './tokenStore'
import type { Room, RoomStatus, MaintenanceDraft, RoomStatusHistory } from '../types/room'

const API_BASE_URL = `${import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'}/api`

type ApiError = {
  message?: string
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

    throw new Error(
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

export function checkIn(roomId: number, guestName: string) {
  return request<{ id: number; roomId: number; roomNumber: string; guestName: string }>(
    `/rooms/${roomId}/check-in`,
    {
      method: 'POST',
     
      body: JSON.stringify({ guestName }),
    },
  )
}
export function putRoomIntoMaintenance(
  roomId: number,
  maintenance: MaintenanceDraft,
) {
  return request<Room>(`/rooms/${roomId}/maintenance`, {
    method: 'PATCH',
    
    body: JSON.stringify(maintenance),
  })
}

export function checkOut(roomId: number) {
  return request<Room>(`/rooms/${roomId}/check-out`, {
    method: 'POST',
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
