import { useEffect, useState } from 'react'
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

  useEffect(() => {
    if (!startDate) return
    const controller = new AbortController()
    let current = true
    getRoomCalendar(startDate, controller.signal).then(
      data => { if (current) setResult({ date: startDate, data }) },
      error => { if (current) setResult({ date: startDate, error: error instanceof Error ? error.message : 'Không tải được sơ đồ phòng' }) },
    )
    return () => { current = false; controller.abort() }
  }, [startDate, attempt])

  const visible = result?.date === startDate ? result : null
  const loading = Boolean(startDate && !visible)
  return (
    <section className="room-calendar" aria-label="Sơ đồ phòng theo ngày">
      <label>Ngày bắt đầu <input type="date" value={startDate} min="0001-01-01" max="9999-12-17"
        onChange={event => { setResult(null); setStartDate(event.target.value) }} /></label>
      <p>14 ngày • Chỉ hiển thị phòng đang hoạt động. Mỗi ô tương ứng một đêm lưu trú.</p>
      <div className="room-calendar-legend" aria-label="Chú giải">
        {Object.entries(labels).map(([status, label]) => <span key={status} className={`calendar-${status}`}>{label}</span>)}
      </div>
      <p>Lịch theo phòng đang gán; chưa thể hiện lịch sử đổi phòng và booking đã trả phòng. Trống trên sơ đồ chưa bảo đảm có thể nhận booking mới.</p>
      {!startDate && <p role="status">Vui lòng chọn ngày bắt đầu.</p>}
      {loading && <p role="status">Đang tải sơ đồ phòng…</p>}
      {visible?.error && <div role="alert">Không tải được sơ đồ phòng: {visible.error} <button type="button"
        onClick={() => { setResult(null); setAttempt(value => value + 1) }}>Thử lại</button></div>}
      {visible?.data && (visible.data.rooms.length === 0 ? <p role="status">Không có phòng đang hoạt động.</p> :
        <div className="room-calendar-scroll" tabIndex={0} role="region" aria-label="Lưới phòng, cuộn để xem thêm">
          <table>
            <caption>Sơ đồ từ {visible.data.startDate} — {visible.data.dates.at(-1)}</caption>
            <thead><tr><th scope="col">Phòng</th>{visible.data.dates.map(date => <th scope="col" key={date}><time dateTime={date}>{date.slice(8)}/{date.slice(5, 7)}</time></th>)}</tr></thead>
            <tbody>{visible.data.rooms.map(room => <tr key={room.roomId}>
              <th scope="row">{room.roomNumber}<small>{room.roomType}</small></th>
              {room.cells.map(cell => <td key={cell.date} className={`calendar-${cell.status}`}>{labels[cell.status]}</td>)}
            </tr>)}</tbody>
          </table>
        </div>)}
    </section>
  )
}
