import { useCallback, useEffect, useRef, useState } from 'react'
import { AuthSessionExpiredError } from '../services/apiClient'
import {
  getNotificationInbox,
  getNotificationUnreadCount,
  markNotificationRead,
  NotificationStreamAuthorizationError,
  openNotificationStream,
  type NotificationItem,
} from '../services/notificationService'
import './NotificationBell.css'

const PAGE_SIZE = 5
const POLL_INTERVAL_MS = 3_000

function formatTime(value: string): string {
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(date)
}

function notificationTitle(item: NotificationItem): string {
  const room = item.roomNumber?.trim()
  const roomLabel = room ? `Phòng ${room}` : 'Một phòng'
  if (item.eventType === 'MAINTENANCE_CLEANED') return `${roomLabel} đã vệ sinh xong sau bảo trì`
  if (item.eventType === 'MAINTENANCE_REPORTED') return 'Có yêu cầu bảo trì mới'
  if (item.eventType === 'ROOM_CLEANED') return `${roomLabel} đã được dọn sạch`
  return 'Có thông báo mới'
}

export function NotificationBell({ userId, onOpenMaintenance }: {
  userId: number
  onOpenMaintenance?: (id: number) => void
}) {
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<NotificationItem[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [readingIds, setReadingIds] = useState<number[]>([])
  const [streamActive, setStreamActive] = useState(false)
  const generationRef = useRef(0)
  const currentPageRef = useRef(0)
  const requestRef = useRef<AbortController | null>(null)
  const requestSequenceRef = useRef(0)
  const sessionRef = useRef<AbortController | null>(null)

  const refresh = useCallback(async (requestedPage: number, foreground = false) => {
    const session = sessionRef.current
    if (!session || session.signal.aborted) return
    const generation = generationRef.current
    const sequence = ++requestSequenceRef.current
    requestRef.current?.abort()
    const controller = new AbortController()
    requestRef.current = controller
    if (foreground) {
      setLoading(true)
      setItems([])
      setError('')
    }
    try {
      const [inbox, unread] = await Promise.all([
        getNotificationInbox(requestedPage, PAGE_SIZE, controller.signal),
        getNotificationUnreadCount(controller.signal),
      ])
      if (controller.signal.aborted || session.signal.aborted || generation !== generationRef.current || sequence !== requestSequenceRef.current) return
      setItems(inbox.content)
      setPage(inbox.page)
      currentPageRef.current = inbox.page
      setTotalPages(inbox.totalPages)
      setTotalElements(inbox.totalElements)
      setUnreadCount(unread.unreadCount)
      setError('')
    } catch (cause) {
      if (controller.signal.aborted || session.signal.aborted || generation !== generationRef.current || sequence !== requestSequenceRef.current) return
      if (!(cause instanceof AuthSessionExpiredError)) setError('Không thể tải thông báo. Vui lòng thử lại.')
    } finally {
      if (sequence === requestSequenceRef.current && generation === generationRef.current) {
        setLoading(false)
        requestRef.current = null
      }
    }
  }, [])

  const refreshCurrent = useCallback(() => {
    void refresh(currentPageRef.current)
  }, [refresh])

  useEffect(() => {
    const generation = ++generationRef.current
    const session = new AbortController()
    sessionRef.current = session
    currentPageRef.current = 0
    requestRef.current?.abort()
    setItems([])
    setUnreadCount(0)
    setPage(0)
    setTotalPages(0)
    setTotalElements(0)
    setOpen(false)
    setReadingIds([])
    setStreamActive(false)
    setError('')
    void refresh(0, true)

    let pollingTimer: number | undefined
    const startPolling = () => {
      if (session.signal.aborted || generation !== generationRef.current || pollingTimer !== undefined) return
      setStreamActive(false)
      pollingTimer = window.setInterval(() => {
        if (!document.hidden) refreshCurrent()
      }, POLL_INTERVAL_MS)
    }
    void openNotificationStream(session.signal, refreshCurrent, () => {
      if (!session.signal.aborted && generation === generationRef.current) setStreamActive(true)
    }).then(startPolling).catch((cause: unknown) => {
      if (!session.signal.aborted && !(cause instanceof AuthSessionExpiredError)
        && !(cause instanceof NotificationStreamAuthorizationError)) startPolling()
    })
    return () => {
      session.abort()
      requestRef.current?.abort()
      if (pollingTimer !== undefined) window.clearInterval(pollingTimer)
      if (sessionRef.current === session) sessionRef.current = null
      if (generation === generationRef.current) generationRef.current++
    }
  }, [userId, refresh, refreshCurrent])

  async function handleNotificationClick(item: NotificationItem) {
    if (item.sourceType === 'MAINTENANCE_REQUEST' && onOpenMaintenance) {
      onOpenMaintenance(item.sourceId)
      setOpen(false)
    }
    if (item.readAt || readingIds.includes(item.id)) return
    const generation = generationRef.current
    const session = sessionRef.current
    if (!session || session.signal.aborted) return
    const controller = new AbortController()
    const onAbort = () => controller.abort()
    session.signal.addEventListener('abort', onAbort, { once: true })
    setReadingIds((ids) => [...ids, item.id])
    const readAt = new Date().toISOString()
    setItems((current) => current.map((entry) => entry.id === item.id ? { ...entry, readAt } : entry))
    setUnreadCount((count) => Math.max(0, count - 1))
    try {
      await markNotificationRead(item.id, controller.signal)
    } catch {
      if (generation === generationRef.current && !controller.signal.aborted) {
        setItems((current) => current.map((entry) => entry.id === item.id ? { ...entry, readAt: null } : entry))
        setError('Không thể cập nhật trạng thái đã đọc. Vui lòng thử lại.')
        refreshCurrent()
      }
    } finally {
      session.signal.removeEventListener('abort', onAbort)
      if (generation === generationRef.current) setReadingIds((ids) => ids.filter((id) => id !== item.id))
    }
  }

  const first = totalElements === 0 ? 0 : page * PAGE_SIZE + 1
  const last = totalElements === 0 ? 0 : Math.min(totalElements, page * PAGE_SIZE + items.length)
  return (
    <div className="notification-widget">
      <button
        type="button"
        className="notification-bell"
        aria-label={unreadCount ? `Thông báo, ${unreadCount} chưa đọc` : 'Thông báo'}
        aria-expanded={open}
        aria-controls="notification-panel"
        onClick={() => {
          setOpen((current) => !current)
          if (!open) void refresh(currentPageRef.current, items.length === 0)
        }}
      >
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4" /></svg>
        {unreadCount > 0 && <span className="notification-badge">{unreadCount > 99 ? '99+' : unreadCount}</span>}
      </button>
      {open && (
        <section className="notification-panel" id="notification-panel" aria-label="Thông báo">
          <header className="notification-panel-header">
            <div><h2>Thông báo</h2><span>{streamActive ? 'Đang kết nối trực tiếp' : 'Đang đồng bộ'}</span></div>
            <button type="button" aria-label="Đóng thông báo" onClick={() => setOpen(false)}>×</button>
          </header>
          {error && <p className="notification-error" role="alert">{error}</p>}
          <div className="notification-list" aria-busy={loading}>
            {loading ? (
              <p className="notification-message" aria-live="polite">Đang tải thông báo…</p>
            ) : items.length === 0 ? (
              <p className="notification-message">Chưa có thông báo.</p>
            ) : items.map((item) => (
              <button
                className={`notification-item${item.readAt ? ' is-read' : ' is-unread'}${item.eventType === 'MAINTENANCE_REPORTED' ? ' is-maintenance' : ' is-cleaned'}`}
                key={item.id}
                type="button"
                aria-label={`${notificationTitle(item)}, ${item.readAt ? 'đã đọc' : 'chưa đọc'}`}
                onClick={() => void handleNotificationClick(item)}
                disabled={readingIds.includes(item.id)}
              >
                <span className="notification-event-icon" aria-hidden="true">{item.eventType === 'MAINTENANCE_REPORTED' ? '!' : '✓'}</span>
                <span className="notification-item-content">
                  <strong>{notificationTitle(item)}</strong>
                  {item.actorName?.trim() && <span>Nhân viên: {item.actorName.trim()}</span>}
                  <time dateTime={item.createdAt}>{formatTime(item.createdAt)}</time>
                  <span className="notification-read-state">{item.readAt ? 'Đã đọc' : 'Chưa đọc'}</span>
                </span>
              </button>
            ))}
          </div>
          <footer className="notification-pagination">
            <span>{loading ? 'Đang tải…' : `Hiển thị ${first}–${last} / ${totalElements}`}</span>
            {totalPages > 1 && (
              <div className="notification-pagination-controls">
                <span>Trang {page + 1} / {totalPages}</span>
                <button type="button" disabled={loading || page <= 0} onClick={() => void refresh(page - 1, true)}>Trước</button>
                <button type="button" disabled={loading || page + 1 >= totalPages} onClick={() => void refresh(page + 1, true)}>Tiếp</button>
              </div>
            )}
          </footer>
        </section>
      )}
    </div>
  )
}
