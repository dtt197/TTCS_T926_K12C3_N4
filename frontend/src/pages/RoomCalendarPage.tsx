import { memo, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { ApiRequestError } from '../services/apiClient'
import { getBookingDetails, getBookingSummary, markBookingNoShow, type BookingDetailResponse } from '../services/bookingService'
import { createRoomCalendarPlan, mergeRoomCalendarResponses, type RoomCalendarPlan } from '../services/roomCalendarRanges'
import { getRoomCalendar, type CalendarStatus, type RoomCalendar } from '../services/roomCalendarService'
import type { BookingListItem } from '../types/booking'
import { isNoShowEligible } from './bookingActionVisibility'
import { BookingNoShowUndoButton } from '../components/BookingNoShowUndoButton'
import './RoomCalendarPage.css'

const labels: Record<CalendarStatus, string> = {
  AVAILABLE: 'Trống', BOOKED: 'Có booking', MAINTENANCE: 'Bảo trì',
}

const BOOKING_STATUS_LABELS: Record<string, string> = {
  CHO_XAC_NHAN: 'Chờ xác nhận',
  DA_XAC_NHAN: 'Đã xác nhận',
  DA_HUY: 'Đã huỷ',
  DA_NHAN_PHONG: 'Đã nhận phòng',
  DA_TRA_PHONG: 'Đã trả phòng',
  DA_HET_HAN: 'Đã hết hạn',
  NO_SHOW: 'Không đến',
}

function today() {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

const MAX_SELECTED_END_DATE = '9999-12-31'

function addCalendarDays(value: string, days: number) {
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(0)
  date.setUTCFullYear(year, month - 1, day + days)
  return [date.getUTCFullYear(), String(date.getUTCMonth() + 1).padStart(2, '0'), String(date.getUTCDate()).padStart(2, '0')].join('-')
}

function formatDate(value: string) {
  const [year, month, day] = value.split('-')
  return `${day}/${month}/${year}`
}

const WEEKDAY_LABELS = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7']

function getWeekdayLabel(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(0)
  date.setUTCFullYear(year, month - 1, day)
  return WEEKDAY_LABELS[date.getUTCDay()]
}

function getNextLocalMidnightDelay() {
  const now = new Date()
  const nextMidnight = new Date(now.getFullYear(), now.getMonth(), now.getDate() + 1)
  return Math.max(1, nextMidnight.getTime() - now.getTime())
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value)
}

function formatSeconds(milliseconds: number) {
  return (milliseconds / 1000).toLocaleString('vi-VN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

type Result = { range: string; data?: RoomCalendar; error?: string }
type LoadProgress = { range: string; completed: number; total: number; error?: string }
type PopoverPosition = { left: number; top: number }
type CalendarRoom = RoomCalendar['rooms'][number]

/** S3-10 Lát 3: hộp chi tiết booking mở từ một ô của sơ đồ. */
type Detail = {
  cellKey: string
  bookingId: number
  roomNumber: string
  booking?: BookingListItem
  extra?: BookingDetailResponse
  error?: string
}

type CalendarRowProps = {
  room: CalendarRoom
  /** Ngày của ô đang mở khung thông tin trong hàng này, null nếu hàng không có ô nào đang mở. */
  expandedDate: string | null
  popoverPosition: PopoverPosition | null
  onEnter: (key: string) => void
  onLeave: (key: string) => void
  onFocusCell: (key: string) => void
  onToggle: (key: string) => void
  onOpen: (key: string, bookingId: number, roomNumber: string) => void
  registerTrigger: (key: string, element: HTMLButtonElement | null) => void
}

/** S3-10 Lát 4: mỗi hàng chỉ dựng lại khi dữ liệu hoặc ô đang mở của chính hàng đó thay đổi. */
const CalendarRow = memo(function CalendarRow({
  room, expandedDate, popoverPosition, onEnter, onLeave, onFocusCell, onToggle, onOpen, registerTrigger,
}: CalendarRowProps) {
  return (
    <tr>
      <th scope="row">{room.roomNumber}<small>{room.roomType}</small></th>
      {room.cells.map(cell => {
        const key = `${room.roomId}:${cell.date}`
        const canShow = cell.status === 'BOOKED'
        const expanded = expandedDate === cell.date
        const details = canShow && (cell.guestName || cell.bookingCode)
        const bookingId = cell.bookingId
        return <td key={cell.date} className={`calendar-${cell.status}`}>
          {canShow ? <button type="button" className="calendar-cell-trigger"
            aria-label={`${labels[cell.status]}, phòng ${room.roomNumber}, ${cell.date}${bookingId != null ? ', bấm để xem chi tiết booking' : ''}`}
            aria-haspopup={bookingId != null ? 'dialog' : undefined}
            aria-expanded={Boolean(details && expanded)}
            onMouseEnter={() => { if (details) onEnter(key) }}
            onMouseLeave={() => onLeave(key)}
            onFocus={() => { if (details) onFocusCell(key) }}
            onClick={() => {
              if (bookingId != null) onOpen(key, bookingId, room.roomNumber)
              else if (details) onToggle(key)
            }}
            ref={element => registerTrigger(key, element)}>
            {labels[cell.status]}
            {details && expanded && <span className="calendar-booking-popover" role="status" style={popoverPosition ? { left: popoverPosition.left, top: popoverPosition.top } : undefined}>
              {cell.guestName && <span className="calendar-guest-name">{cell.guestName}</span>}
              {cell.bookingCode && <span className="calendar-booking-code">{cell.bookingCode}</span>}
              {!cell.guestName && <span>Thiếu tên khách</span>}{!cell.bookingCode && <span>Thiếu mã booking</span>}
            </span>}
          </button> : labels[cell.status]}
        </td>
      })}
    </tr>
  )
})

export function RoomCalendarPage() {
  const [todayDate, setTodayDate] = useState(today)
  const [startDate, setStartDate] = useState(today)
  const [endDate, setEndDate] = useState(() => addCalendarDays(today(), 13))
  const [result, setResult] = useState<Result | null>(null)
  const [loadProgress, setLoadProgress] = useState<LoadProgress | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [openCell, setOpenCell] = useState<string | null>(null)
  const [popoverPosition, setPopoverPosition] = useState<PopoverPosition | null>(null)
  const [detail, setDetail] = useState<Detail | null>(null)
  /** S3-10 Lát 4: thời gian từ lúc gửi yêu cầu đến khi lưới hiển thị xong (mili giây). */
  const [loadMs, setLoadMs] = useState<number | null>(null)
  const [noShowLoading, setNoShowLoading] = useState(false)
  const [noShowMessage, setNoShowMessage] = useState<string | null>(null)
  const triggerRefs = useRef(new Map<string, HTMLButtonElement>())
  const detailRequest = useRef(0)
  const calendarRequestGeneration = useRef(0)
  const skipFocusPopover = useRef(false)
  const loadStartedAt = useRef(0)

  useEffect(() => {
    let timeout: ReturnType<typeof setTimeout>
    const updateAtNextLocalMidnight = () => {
      setTodayDate(today())
      timeout = setTimeout(updateAtNextLocalMidnight, getNextLocalMidnightDelay())
    }
    timeout = setTimeout(updateAtNextLocalMidnight, getNextLocalMidnightDelay())
    return () => clearTimeout(timeout)
  }, [])

  useEffect(() => {
    if (!openCell) return
    const trigger = triggerRefs.current.get(openCell)
    if (!trigger) return
    const rect = trigger.getBoundingClientRect()
    setPopoverPosition({ left: Math.min(Math.max(rect.left + rect.width / 2, 140), window.innerWidth - 140),
      top: rect.top > 150 ? rect.top - 8 : Math.min(rect.bottom + 8, window.innerHeight - 20) })
  }, [openCell])
  useEffect(() => {
    if (!openCell) return
    const closeOnEscape = (event: KeyboardEvent) => { if (event.key === 'Escape') setOpenCell(null) }
    document.addEventListener('keydown', closeOnEscape)
    return () => document.removeEventListener('keydown', closeOnEscape)
  }, [openCell])
  useEffect(() => {
    if (!detail) return
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { detailRequest.current += 1; setDetail(null) }
    }
    document.addEventListener('keydown', closeOnEscape)
    return () => document.removeEventListener('keydown', closeOnEscape)
  }, [detail])

  const range = `${startDate}/${endDate}`
  const planResult = useMemo((): { plan: RoomCalendarPlan; error?: never } | { plan?: never; error: string } => {
    if (!startDate || !endDate) return { error: 'Vui lòng chọn cả ngày bắt đầu và ngày kết thúc.' }
    if (endDate < startDate) return { error: 'Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.' }
    try {
      return { plan: createRoomCalendarPlan(startDate, endDate) }
    } catch (error) {
      return { error: error instanceof Error ? error.message : 'Khoảng ngày không hợp lệ.' }
    }
  }, [startDate, endDate])
  const rangeError = planResult.error ?? null
  const selectedDays = planResult.plan?.totalDays ?? null
  const displayEndDate = planResult.plan?.displayEndDate ?? null

  useEffect(() => {
    if (rangeError || !planResult.plan) return
    const controller = new AbortController()
    const generation = calendarRequestGeneration.current + 1
    calendarRequestGeneration.current = generation
    const isCurrent = () => calendarRequestGeneration.current === generation && !controller.signal.aborted
    const plan = planResult.plan
    loadStartedAt.current = performance.now()

    const segment = plan.segments[0]
    void getRoomCalendar(segment.startDate, segment.days, controller.signal).then(
      response => {
        if (!isCurrent()) return
        try {
          const data = mergeRoomCalendarResponses([response], plan)
          setOpenCell(null)
          setPopoverPosition(null)
          setResult({ range, data })
        } catch (error) {
          setLoadProgress({ range, completed: 1, total: 1, error: error instanceof Error ? error.message : 'Không thể ghép dữ liệu sơ đồ phòng.' })
        }
      },
      error => {
        if (!isCurrent()) return
        controller.abort()
        setOpenCell(null)
        setPopoverPosition(null)
        setLoadProgress({ range, completed: 0, total: 1, error: error instanceof Error ? error.message : 'Không tải được sơ đồ phòng.' })
      },
    )

    return () => {
      calendarRequestGeneration.current += 1
      controller.abort()
    }
  }, [planResult, range, rangeError, attempt])

  // S3-10 Lát 4: đo đến khi trình duyệt vẽ xong lưới (sau hai khung hình kể từ lúc dữ liệu được dựng).
  useEffect(() => {
    if (!result?.data) return
    let second = 0
    const first = requestAnimationFrame(() => {
      second = requestAnimationFrame(() => setLoadMs(Math.round(performance.now() - loadStartedAt.current)))
    })
    return () => { cancelAnimationFrame(first); cancelAnimationFrame(second) }
  }, [result])

  const handleEnter = useCallback((key: string) => setOpenCell(key), [])
  const handleLeave = useCallback((key: string) => setOpenCell(current => current === key ? null : current), [])
  const handleFocusCell = useCallback((key: string) => { if (!skipFocusPopover.current) setOpenCell(key) }, [])
  const handleToggle = useCallback((key: string) => setOpenCell(current => current === key ? null : key), [])
  const registerTrigger = useCallback((key: string, element: HTMLButtonElement | null) => {
    if (element) triggerRefs.current.set(key, element)
    else triggerRefs.current.delete(key)
  }, [])

  /** Bấm vào ô có booking: tải dữ liệu hiện có của booking đó và mở hộp chi tiết; khoảng ngày của sơ đồ giữ nguyên. */
  const openDetail = useCallback((cellKey: string, bookingId: number, roomNumber: string) => {
    const requestId = detailRequest.current + 1
    detailRequest.current = requestId
    setOpenCell(null)
    setPopoverPosition(null)
    setDetail({ cellKey, bookingId, roomNumber })
    Promise.all([getBookingSummary(bookingId), getBookingDetails(bookingId)]).then(
      ([booking, extra]) => {
        if (detailRequest.current === requestId) setDetail({ cellKey, bookingId, roomNumber, booking, extra })
      },
      (error: unknown) => {
        if (detailRequest.current !== requestId) return
        let message = error instanceof Error ? error.message : 'Không tải được chi tiết booking.'
        if (error instanceof ApiRequestError && error.status === 404) {
          message = 'Booking không còn tồn tại. Sơ đồ phòng đã được tải lại.'
          setAttempt(value => value + 1)
        } else if (error instanceof ApiRequestError && error.status === 403) {
          message = 'Bạn không có quyền xem chi tiết booking này.'
        }
        setDetail({ cellKey, bookingId, roomNumber, error: message })
      },
    )
  }, [])

  function closeDetail() {
    detailRequest.current += 1
    const cellKey = detail?.cellKey
    setDetail(null)
    setOpenCell(null)
    setPopoverPosition(null)
    setNoShowMessage(null)
    if (cellKey) {
      // Trả focus về ô vừa bấm nhưng không bật lại khung tên khách của Lát 2.
      skipFocusPopover.current = true
      triggerRefs.current.get(cellKey)?.focus()
      skipFocusPopover.current = false
    }
  }

  async function handleCalendarNoShow(bookingId: number) {
    if (noShowLoading) return
    const confirmed = window.confirm('Bạn có chắc chắn muốn đánh dấu khách không đến cho booking này? Thao tác này sẽ giải phóng phòng trên sơ đồ ngay lập tức.')
    if (!confirmed) return
    setNoShowLoading(true)
    setNoShowMessage(null)
    try {
      await markBookingNoShow(bookingId)
      setNoShowMessage('Đã đánh dấu khách không đến và giải phóng phòng thành công.')
      setAttempt(v => v + 1)
      if (detail && detail.booking) {
        setDetail({
          ...detail,
          booking: { ...detail.booking, status: 'NO_SHOW', roomNumber: null },
        })
      }
    } catch (err) {
      alert(err instanceof Error ? err.message : 'Không thể đánh dấu khách không đến.')
    } finally {
      setNoShowLoading(false)
    }
  }

  const visible = result?.range === range ? result : null
  const visibleLoadProgress = loadProgress?.range === range ? loadProgress : null
  const loading = !rangeError && !visible
  return (
    <section className="room-calendar" aria-label="Sơ đồ phòng theo ngày" aria-busy={loading}>
      <header className="room-calendar-header">
        <div className="room-calendar-title-row">
          <span className="room-calendar-title-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none">
              <rect x="3.5" y="5" width="17" height="15.5" rx="2.5" />
              <path d="M7.5 3.5v3M16.5 3.5v3M4 9.5h16M8 13h2m4 0h2M8 16.5h2" />
            </svg>
          </span>
          <div>
            <h1>Sơ đồ phòng theo ngày</h1>
            <p>Chọn khoảng ngày cần xem tình trạng phòng.</p>
          </div>
        </div>
        <div className="room-calendar-controls">
        <div className="room-calendar-date-fields">
          <label htmlFor="room-calendar-start">Ngày bắt đầu
            <input id="room-calendar-start" type="date" value={startDate} min="0001-01-01" max={MAX_SELECTED_END_DATE}
              onChange={event => {
                const nextStartDate = event.target.value
                setOpenCell(null); setPopoverPosition(null); setLoadMs(null); setResult(null); setLoadProgress(null)
                setStartDate(nextStartDate)
              }} />
          </label>
          <label htmlFor="room-calendar-end">Ngày kết thúc
            <input id="room-calendar-end" type="date" value={endDate} min={startDate || '0001-01-01'} max={MAX_SELECTED_END_DATE}
              onChange={event => { setOpenCell(null); setPopoverPosition(null); setLoadMs(null); setResult(null); setLoadProgress(null); setEndDate(event.target.value) }} />
          </label>
        </div>
        </div>
        {planResult.plan && selectedDays != null && displayEndDate && <div className="room-calendar-info-bar" aria-label="Tóm tắt sơ đồ phòng">
          <p className="room-calendar-range-summary">Sơ đồ hiển thị từ <strong>{formatDate(startDate)}</strong> đến <strong>{formatDate(displayEndDate)}</strong></p>
          <span className="room-calendar-day-count">{selectedDays} ngày</span>
          <div className="room-calendar-legend" aria-label="Chú giải trạng thái">
            {Object.entries(labels).map(([status, label]) => <span key={status} className={`calendar-${status}`}>
              <svg viewBox="0 0 16 16" fill="none" aria-hidden="true">
                {status === 'AVAILABLE' && <><path d="M8 14s5-2.5 5-7V3L8 1 3 3v4c0 4.5 5 7 5 7Z" /><path d="m5.5 7.5 1.6 1.6 3.4-3.6" /></>}
                {status === 'BOOKED' && <><rect x="2" y="3" width="12" height="10" rx="2" /><path d="M5 6h6M5 9h4" /></>}
                {status === 'MAINTENANCE' && <><path d="M9.2 2.2a4 4 0 0 0 4.6 5.1l-6.5 6.5a1.7 1.7 0 0 1-2.4-2.4l6.5-6.5a4 4 0 0 0-5.1-4.6l2.3 2.3-2.1 2.1-2.3-2.3a4 4 0 0 0 5-0.2Z" /></>}
              </svg>
              {label}
            </span>)}
          </div>
        </div>}
      </header>
      <div className="room-calendar-guidance">
        <details className="room-calendar-guidance-card">
          <summary aria-label="Mở hoặc đóng hướng dẫn cách đọc sơ đồ">
          <span className="room-calendar-guidance-icon" aria-hidden="true">
            <svg viewBox="0 0 20 20" fill="none"><circle cx="10" cy="10" r="7.5" /><path d="M10 9v4m0-7h.01" /></svg>
          </span>
            <strong>Cách đọc sơ đồ</strong>
          </summary>
          <div className="room-calendar-guidance-content">
            <ul>
              <li>Chỉ hiển thị phòng đang hoạt động; mỗi ô đại diện cho một đêm lưu trú.</li>
              <li>Sơ đồ theo phòng được gán, không thể hiện đầy đủ lịch sử đổi phòng và booking đã trả phòng.</li>
              <li>Ô Trống không bảo đảm có thể nhận booking mới. Nhấn ô Có booking để xem chi tiết.</li>
            </ul>
          </div>
        </details>
      </div>
      {rangeError && <p className="room-calendar-range-error" role="alert">{rangeError}</p>}
      {loading && !visibleLoadProgress?.error &&
        <p className="room-calendar-load-status" role="status">Đang tải {visibleLoadProgress?.completed ?? 0}/{visibleLoadProgress?.total ?? planResult.plan?.segments.length ?? 0} đoạn…</p>}
      {visibleLoadProgress?.error && <div className="room-calendar-load-status" role="alert">
        Không tải được đầy đủ sơ đồ phòng: {visibleLoadProgress.error} <button type="button"
          onClick={() => { setOpenCell(null); setPopoverPosition(null); setLoadMs(null); setResult(null); setLoadProgress(null); setAttempt(value => value + 1) }}>Thử lại</button>
      </div>}
      {visible?.data && loadMs != null && (
        <p className="room-calendar-timing">
          Sơ đồ {visible.data.rooms.length} phòng × {visible.data.dates.length} ngày tải xong trong {formatSeconds(loadMs)} giây.
        </p>
      )}
      {visible?.data && (visible.data.rooms.length === 0 ? <p role="status">Không có phòng đang hoạt động.</p> :
        <div className="room-calendar-scroll" tabIndex={0} role="region" aria-label="Lưới phòng, cuộn để xem thêm">
          <table>
            <caption>Sơ đồ từ {visible.data.startDate} — {visible.data.dates.at(-1)}</caption>
            <thead><tr><th scope="col">Phòng</th>{visible.data.dates.map(date => {
              const isToday = date === todayDate
              const weekday = getWeekdayLabel(date)
              return <th scope="col" key={date} className={`calendar-date-header${isToday ? ' calendar-date-header--today' : ''}${weekday === 'T7' || weekday === 'CN' ? ' calendar-date-header--weekend' : ''}`}>
                <time dateTime={date} title={formatDate(date)}>
                  <span className="calendar-date-weekday">{weekday}</span>
                  <span className="calendar-date-day">{date.slice(8)}/{date.slice(5, 7)}</span>
                  {isToday && <span className="calendar-today-label">Hôm nay</span>}
                </time>
              </th>
            })}</tr></thead>
            <tbody>{visible.data.rooms.map(room => {
              const prefix = `${room.roomId}:`
              const expandedDate = openCell?.startsWith(prefix) ? openCell.slice(prefix.length) : null
              return <CalendarRow key={room.roomId} room={room} expandedDate={expandedDate}
                popoverPosition={expandedDate ? popoverPosition : null}
                onEnter={handleEnter} onLeave={handleLeave} onFocusCell={handleFocusCell} onToggle={handleToggle}
                onOpen={openDetail} registerTrigger={registerTrigger} />
            })}</tbody>
          </table>
        </div>)}

      {detail && (
        <div className="calendar-detail-backdrop" onClick={closeDetail}>
          <div className="calendar-detail-dialog" role="dialog" aria-modal="true" aria-labelledby="calendar-detail-title"
            onClick={event => event.stopPropagation()}>
            <div className="calendar-detail-heading">
              <h3 id="calendar-detail-title">Chi tiết booking{detail.booking ? ` ${detail.booking.bookingCode}` : ''}</h3>
              <button type="button" className="calendar-detail-close" aria-label="Đóng" onClick={closeDetail} autoFocus>×</button>
            </div>
            {!detail.booking && !detail.error && <p role="status">Đang tải chi tiết booking…</p>}
            {detail.error && <p role="alert" className="calendar-detail-error">{detail.error}</p>}
            {detail.booking && (
              <dl className="calendar-detail-grid">
                <div><dt>Khách</dt><dd>{detail.booking.guestName}</dd></div>
                <div><dt>Số điện thoại</dt><dd>{detail.booking.guestPhone || 'Chưa có'}</dd></div>
                <div><dt>Trạng thái</dt><dd>{BOOKING_STATUS_LABELS[detail.booking.status] ?? detail.booking.status}</dd></div>
                <div><dt>Loại phòng</dt><dd>{detail.booking.roomTypeNameSnapshot}</dd></div>
                <div><dt>Phòng</dt><dd>{detail.booking.roomNumber ?? detail.roomNumber}</dd></div>
                <div><dt>Ngày nhận phòng</dt><dd>{formatDate(detail.booking.checkInDate)}</dd></div>
                <div><dt>Ngày trả phòng</dt><dd>{formatDate(detail.booking.checkOutDate)}</dd></div>
                <div><dt>Tổng tiền</dt><dd>{formatCurrency(detail.booking.totalAmount)}</dd></div>
                <div><dt>Tiền cọc hiện tại</dt><dd>{detail.extra?.currentDepositTotal != null ? formatCurrency(detail.extra.currentDepositTotal) : 'Chưa ghi nhận'}</dd></div>
              </dl>
            )}
            {noShowMessage && (
              <p role="status" style={{ color: '#059669', fontWeight: 500, margin: '8px 0', fontSize: '14px' }}>{noShowMessage}</p>
            )}
            <div className="calendar-detail-actions" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              {detail.booking && (detail.booking.status === 'CHO_XAC_NHAN' || detail.booking.status === 'DA_XAC_NHAN') && (() => {
                const eligibility = isNoShowEligible(detail.booking)
                return (
                  <button
                    type="button"
                    id={`calendar-no-show-btn-${detail.bookingId}`}
                    className={`calendar-no-show-button ${eligibility.eligible ? 'is-active' : ''}`}
                    disabled={!eligibility.eligible || noShowLoading}
                    title={eligibility.reason || 'Đánh dấu khách không đến'}
                    onClick={() => handleCalendarNoShow(detail.bookingId)}
                    style={{
                      backgroundColor: eligibility.eligible ? '#d97706' : '#9ca3af',
                      color: '#ffffff',
                      border: 'none',
                      borderRadius: '6px',
                      padding: '8px 14px',
                      fontWeight: 500,
                      cursor: eligibility.eligible && !noShowLoading ? 'pointer' : 'not-allowed',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '6px',
                    }}
                  >
                    <span aria-hidden="true">⏱</span> {noShowLoading ? 'Đang xử lý…' : 'Khách không đến'}
                  </button>
                )
              })()}
              {detail.booking && detail.booking.status === 'NO_SHOW' && (
                <BookingNoShowUndoButton
                  bookingId={detail.bookingId}
                  variant="detail"
                  onSuccess={() => {
                    setAttempt((v) => v + 1)
                    closeDetail()
                  }}
                />
              )}
              <button type="button" onClick={closeDetail}>Quay lại sơ đồ</button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}
