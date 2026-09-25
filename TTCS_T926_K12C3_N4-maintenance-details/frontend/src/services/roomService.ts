import type { MaintenanceDraft, Room, RoomStatus } from '../types/room'

const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

type ApiError = {
  message?: string
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...options?.headers,
    },
    ...options,
  })

  const body = (await response.json().catch(() => null)) as T & ApiError
  if (!response.ok) {
    throw new Error(body?.message ?? 'Không thể kết nối với máy chủ.')
  }
  return body
}

export function getRooms() {
  return request<Room[]>('/rooms')
}

export function updateRoomStatus(roomId: number, status: RoomStatus) {
  return request<Room>(`/rooms/${roomId}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  })
}

export function putRoomIntoMaintenance(roomId: number, maintenance: MaintenanceDraft) {
  return request<Room>(`/rooms/${roomId}/maintenance`, {
    method: 'PATCH',
    body: JSON.stringify(maintenance),
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