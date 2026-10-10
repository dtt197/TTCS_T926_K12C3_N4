import { useEffect, useRef, useState } from 'react'
import { getRoomCalendar, type CalendarStatus, type RoomCalendar } from '../services/roomCalendarService'
import './RoomCalendarPage.css'

const labels: Record<CalendarStatus, string> = {
  AVAILABLE: 'Trống', BOOKED: 'Có booking', MAINTENANCE: 'Bảo trì',
}

function today() {
  const date = new Date()
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

type Result = { date: string; data?: RoomCalendar; error?: string }

export function RoomCalendarPage() {
  const [startDate, setStartDate] = useState(today)
  const [result, setResult] = useState<Result | null>(null)
  const [attempt, setAttempt] = useState(0)
  const [openCell, setOpenCell] = useState<string | null>(null)
  const [popoverPosition, setPopoverPosition] = useState<{ left: number; top: number } | null>(null)
  const triggerRefs = useRef(new Map<string, HTMLButtonElement>())

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
    if (!startDate) return
    const controller = new AbortController()
    let current = true
    getRoomCalendar(startDate, controller.signal).then(
      data => { if (current) { setOpenCell(null); setPopoverPosition(null); setResult({ date: startDate, data }) } },
      error => { if (current) { setOpenCell(null); setPopoverPosition(null); setResult({ date: startDate, error: error instanceof Error ? error.message : 'Không tải được sơ đồ phòng' }) } },
    )
    return () => { current = false; controller.abort() }
  }, [startDate, attempt])

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
      <p>Lịch theo phòng đang gán; chưa thể hiện lịch sử đổi phòng và booking đã trả phòng. Trống trên sơ đồ chưa bảo đảm có thể nhận booking mới.</p>
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
                return <td key={cell.date} className={`calendar-${cell.status}`}>
                  {canShow ? <button type="button" className="calendar-cell-trigger" aria-label={`${labels[cell.status]}, phòng ${room.roomNumber}, ${cell.date}`}
                    aria-expanded={Boolean(details && expanded)} onMouseEnter={() => details && setOpenCell(key)}
                    onMouseLeave={() => setOpenCell(current => current === key ? null : current)}
                    onFocus={() => details && setOpenCell(key)} onClick={() => details && setOpenCell(current => current === key ? null : key)}
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
    </section>
  )
}
