import { apiRequest } from './apiClient'
import { refreshAccessToken } from './authService'
import { clearAccessToken, getAccessToken, setAccessToken } from './tokenStore'

export type NotificationItem = {
  id: number
  eventType: 'ROOM_CLEANED' | 'MAINTENANCE_REPORTED' | string
  roomId: number
  roomNumber: string | null
  actorUserId: number
  actorName: string | null
  sourceType: string
  sourceId: number
  createdAt: string
  readAt: string | null
}

export type NotificationPage = {
  content: NotificationItem[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type NotificationUnreadCount = { unreadCount: number }

export class NotificationStreamAuthorizationError extends Error {
  readonly status: 401 | 403

  constructor(status: 401 | 403) {
    super(`Notification stream authorization failed (${status})`)
    this.name = 'NotificationStreamAuthorizationError'
    this.status = status
  }
}

export function getNotificationInbox(page = 0, size = 20, signal?: AbortSignal): Promise<NotificationPage> {
  return apiRequest<NotificationPage>(`/api/notifications?page=${page}&size=${size}`, { signal })
}

export function getNotificationUnreadCount(signal?: AbortSignal): Promise<NotificationUnreadCount> {
  return apiRequest<NotificationUnreadCount>('/api/notifications/unread-count', { signal })
}

export function markNotificationRead(id: number, signal?: AbortSignal): Promise<void> {
  return apiRequest<void>(`/api/notifications/${id}/read`, { method: 'PATCH', signal })
}

export async function openNotificationStream(
  signal: AbortSignal,
  onInboxChanged: () => void,
  onConnected: () => void,
): Promise<void> {
  const fetchStream = async (retried: boolean): Promise<Response> => {
    const headers = new Headers({ Accept: 'text/event-stream' })
    const accessToken = getAccessToken()
    if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
    const result = await fetch(`${import.meta.env.VITE_API_BASE_URL ?? ''}/api/notifications/stream`, {
      headers,
      credentials: 'include',
      signal,
    })
    if (result.status === 403) throw new NotificationStreamAuthorizationError(403)
    if (result.status !== 401) return result
    if (!retried) {
      try {
        const refreshed = await refreshAccessToken()
        if (signal.aborted) throw new DOMException('Aborted', 'AbortError')
        setAccessToken(refreshed.accessToken)
        return fetchStream(true)
      } catch {
        if (signal.aborted) throw new DOMException('Aborted', 'AbortError')
        clearAccessToken()
        window.dispatchEvent(new Event('auth:expired'))
        throw new NotificationStreamAuthorizationError(401)
      }
    }
    clearAccessToken()
    window.dispatchEvent(new Event('auth:expired'))
    throw new NotificationStreamAuthorizationError(401)
  }
  const response = await fetchStream(false)

  if (!response.ok || !response.body) {
    if (response.status === 401 || response.status === 403) {
      if (response.status === 401) {
        clearAccessToken()
        window.dispatchEvent(new Event('auth:expired'))
      }
      throw new NotificationStreamAuthorizationError(response.status)
    }
    throw new Error(`Notification stream failed (${response.status})`)
  }
  onConnected()

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true }).replace(/\r\n/g, '\n')
      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        const frame = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        const eventName = frame.split('\n').find((line) => line.startsWith('event:'))?.slice(6).trim()
        if (eventName === 'inbox-changed') onInboxChanged()
        boundary = buffer.indexOf('\n\n')
      }
    }
  } finally {
    reader.releaseLock()
  }
}
