import { useEffect, useRef, useState } from 'react'
import { ApiRequestError } from '../services/apiClient'
import { getBookingDetails, getBookingSummary, type BookingDetailResponse } from '../services/bookingService'
import { getRoomCalendar, type CalendarStatus, type RoomCalendar } from '../services/roomCalendarService'
import type { BookingListItem } from '../types/booking'
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
}

function today() {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function formatDate(value: string) {
  const [year, month, day] = value.split('-')
  return `${day}/${month}/${year}`
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value)
}

type Result = { date: string; data?: RoomCalendar; error?: string }

/** S3-10 Lát 3: hộp chi tiết booking mở từ một ô của sơ đồ. */
type Detail = {
  cellKey: string
  bookingId: number
  roomNumber: string
  booking?: BookingListItem
  extra?: BookingDetailResponse
  error?: string
}

export function RoomCalendarPage() {
  const [startDate, setStartDate] = useState(today)
  const [result, setResult] = useState<Result | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [openCell, setOpenCell] = useState<string | null>(null)
  const [popoverPosition, setPopoverPosition] = useState<{ left: number; top: number } | null>(null)
  const [detail, setDetail] = useState<Detail | null>(null)
  const triggerRefs = useRef(new Map<string, HTMLButtonElement>())
  const detailRequest = useRef(0)
  const skipFocusPopover = useRef(false)

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

  useEffect(() => {
    if (!startDate) return
    const controller = new AbortController()
    let current = true
    getRoomCalendar(startDate, controller.signal).then(
      data => { if (current) { setOpenCell(null); setPopoverPosition(null); setResult({ date: startDate, data }) } },
      error => { if (current) { setOpenCell(null); setPopoverPosition(null); setResult({ date: startDate, error: error instanceof Error ? error.message : 'Không tải được sơ đồ phòng' }) } },
    )
    return () => { current = false; controller.abort() }
  }, [startDate, attempt])

  /** Bấm vào ô có booking: tải dữ liệu hiện có của booking đó và mở hộp chi tiết; khoảng ngày của sơ đồ giữ nguyên. */
  function openDetail(cellKey: string, bookingId: number, roomNumber: string) {
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
  }

  function closeDetail() {
    detailRequest.current += 1
    const cellKey = detail?.cellKey
    setDetail(null)
    setOpenCell(null)
    setPopoverPosition(null)
    if (cellKey) {
      // Trả focus về ô vừa bấm nhưng không bật lại khung tên khách của Lát 2.
      skipFocusPopover.current = true
      triggerRefs.current.get(cellKey)?.focus()
      skipFocusPopover.current = false
    }
  }

  const visible = result?.date === startDate ? result : null
  const loading = Boolean(startDate && !visible)
  return (
    <section className="room-calendar" aria-label="Sơ đồ phòng theo ngày">
      <label>Ngày bắt đầu <input type="date" value={startDate} min="0001-01-01" max="9999-12-17"
        onChange={event => { setOpenCell(null); setPopoverPosition(null); setResult(null); setStartDate(event.target.value) }} /></label>
      <p>14 ngày • Chỉ hiển thị phòng đang hoạt động. Mỗi ô tương ứng một đêm lưu trú.</p>
      <div className="room-calendar-legend" aria-label="Chú giải">
        {Object.entries(labels).map(([status, label]) => <span key={status} className={`calendar-${status}`}>{label}</span>)}
      </div>
      <p>Lịch theo phòng đang gán; chưa thể hiện lịch sử đổi phòng và booking đã trả phòng. Trống trên sơ đồ chưa bảo đảm có thể nhận booking mới. Bấm vào ô có booking để xem chi tiết.</p>
      {!startDate && <p role="status">Vui lòng chọn ngày bắt đầu.</p>}
      {loading && <p role="status">Đang tải sơ đồ phòng…</p>}
      {visible?.error && <div role="alert">Không tải được sơ đồ phòng: {visible.error} <button type="button"
        onClick={() => { setOpenCell(null); setPopoverPosition(null); setResult(null); setAttempt(value => value + 1) }}>Thử lại</button></div>}
      {visible?.data && (visible.data.rooms.length === 0 ? <p role="status">Không có phòng đang hoạt động.</p> :
        <div className="room-calendar-scroll" tabIndex={0} role="region" aria-label="Lưới phòng, cuộn để xem thêm">
          <table>
            <caption>Sơ đồ từ {visible.data.startDate} — {visible.data.dates.at(-1)}</caption>
            <thead><tr><th scope="col">Phòng</th>{visible.data.dates.map(date => <th scope="col" key={date}><time dateTime={date}>{date.slice(8)}/{date.slice(5, 7)}</time></th>)}</tr></thead>
            <tbody>{visible.data.rooms.map(room => <tr key={room.roomId}>
              <th scope="row">{room.roomNumber}<small>{room.roomType}</small></th>
              {room.cells.map(cell => {
                const key = `${room.roomId}:${cell.date}`
                const canShow = cell.status === 'BOOKED'
                const expanded = openCell === key
                const details = canShow && (cell.guestName || cell.bookingCode)
                const bookingId = cell.bookingId
                return <td key={cell.date} className={`calendar-${cell.status}`}>
                  {canShow ? <button type="button" className="calendar-cell-trigger"
                    aria-label={`${labels[cell.status]}, phòng ${room.roomNumber}, ${cell.date}${bookingId != null ? ', bấm để xem chi tiết booking' : ''}`}
                    aria-haspopup={bookingId != null ? 'dialog' : undefined}
                    aria-expanded={Boolean(details && expanded)} onMouseEnter={() => details && setOpenCell(key)}
                    onMouseLeave={() => setOpenCell(current => current === key ? null : current)}
                    onFocus={() => { if (!skipFocusPopover.current && details) setOpenCell(key) }}
                    onClick={() => {
                      if (bookingId != null) openDetail(key, bookingId, room.roomNumber)
                      else if (details) setOpenCell(current => current === key ? null : key)
                    }}
                    ref={element => { if (element) triggerRefs.current.set(key, element); else triggerRefs.current.delete(key) }}>
                    {labels[cell.status]}
                    {details && expanded && <span className="calendar-booking-popover" role="status" style={popoverPosition ? { left: popoverPosition.left, top: popoverPosition.top } : undefined}>
                      {cell.guestName && <span className="calendar-guest-name">{cell.guestName}</span>}
                      {cell.bookingCode && <span className="calendar-booking-code">{cell.bookingCode}</span>}
                      {!cell.guestName && <span>Thiếu tên khách</span>}{!cell.bookingCode && <span>Thiếu mã booking</span>}
                    </span>}
                  </button> : labels[cell.status]}
                </td>
              })}
            </tr>)}</tbody>
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
            <div className="calendar-detail-actions">
              <button type="button" onClick={closeDetail}>Quay lại sơ đồ</button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}