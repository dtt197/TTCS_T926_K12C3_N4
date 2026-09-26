import type { Room, RoomStatus, MaintenanceDraft, RoomStatusHistory } from '../types/room'

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

  const body: unknown = await response.json().catch(() => null)
  if (!response.ok) {
    const errorBody = body as ApiError | null
    throw new Error(errorBody?.message ?? 'Không thể kết nối với máy chủ.')
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
    headers: {
      'X-Operator-Name': 'Lễ tân',
    },
    body: JSON.stringify({ status }),
  })
}

export function checkIn(roomId: number, guestName: string) {
  return request<{ id: number; roomId: number; roomNumber: string; guestName: string }>(
    `/rooms/${roomId}/check-in`,
    {
      method: 'POST',
      headers: {
        'X-Operator-Name': 'Lễ tân',
      },
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
    headers: {
      'X-Operator-Name': 'Lễ tân',
    },
    body: JSON.stringify(maintenance),
  })
}
