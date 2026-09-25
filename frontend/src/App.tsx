import { useEffect, useMemo, useState } from 'react'
import './App.css'
import { checkIn, getRooms, updateRoomStatus } from './services/roomService'
import {
  ROOM_STATUS_HELP,
  ROOM_STATUS_LABELS,
  type Room,
  type RoomStatus,
} from './types/room'

const STATUS_OPTIONS: RoomStatus[] = [
  'TRONG_SACH',
  'TRONG_BAN',
  'DANG_O',
  'BAO_TRI',
]

const DEMO_ROOMS: Room[] = [
  { id: 1, roomNumber: '101', floor: 1, roomType: 'Phòng đôi', status: 'TRONG_SACH', active: true },
  { id: 2, roomNumber: '102', floor: 1, roomType: 'Phòng đôi', status: 'TRONG_BAN', active: true },
  { id: 3, roomNumber: '201', floor: 2, roomType: 'Phòng gia đình', status: 'DANG_O', active: true },
  { id: 4, roomNumber: '202', floor: 2, roomType: 'Phòng đơn', status: 'BAO_TRI', active: true },
]

const statusClassName: Record<RoomStatus, string> = {
  TRONG_SACH: 'clean',
  TRONG_BAN: 'dirty',
  DANG_O: 'occupied',
  BAO_TRI: 'maintenance',
}

function App() {
  const [rooms, setRooms] = useState<Room[]>([])
  const [draftStatuses, setDraftStatuses] = useState<Record<number, RoomStatus>>({})
  const [selectedRoomId, setSelectedRoomId] = useState<number | ''>('')
  const [guestName, setGuestName] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [notice, setNotice] = useState<{ type: 'error' | 'success'; text: string } | null>(null)
  const [isDemoMode, setIsDemoMode] = useState(false)

  useEffect(() => {
    getRooms()
      .then((data) => {
        setRooms(data)
        if (data.length > 0) {
          setSelectedRoomId(data.find((room) => room.status === 'TRONG_SACH')?.id ?? data[0].id)
        }
      })
      .catch(() => {
        setRooms(DEMO_ROOMS)
        setSelectedRoomId(1)
        setIsDemoMode(true)
      })
      .finally(() => setIsLoading(false))
  }, [])

  const counts = useMemo(
    () =>
      STATUS_OPTIONS.reduce(
        (result, status) => {
          result[status] = rooms.filter((room) => room.status === status).length
          return result
        },
        {} as Record<RoomStatus, number>,
      ),
    [rooms],
  )

  const selectedRoom = rooms.find((room) => room.id === selectedRoomId)
  const canCheckIn = selectedRoom?.status === 'TRONG_SACH' && guestName.trim().length > 0

  function showError(text: string) {
    setNotice({ type: 'error', text })
  }

  async function handleStatusUpdate(room: Room) {
    const targetStatus = draftStatuses[room.id] ?? room.status
    if (targetStatus === room.status) {
      setNotice({ type: 'success', text: `Phòng ${room.roomNumber} đã ở trạng thái ${ROOM_STATUS_LABELS[room.status]}.` })
      return
    }

    setIsSaving(true)
    setNotice(null)
    try {
      const savedRoom = isDemoMode
        ? { ...room, status: targetStatus }
        : await updateRoomStatus(room.id, targetStatus)
      setRooms((current) => current.map((item) => (item.id === room.id ? savedRoom : item)))
      setNotice({ type: 'success', text: `Đã cập nhật phòng ${room.roomNumber} sang ${ROOM_STATUS_LABELS[targetStatus]}.` })
    } catch (error) {
      showError(error instanceof Error ? error.message : 'Không thể cập nhật trạng thái phòng.')
    } finally {
      setIsSaving(false)
    }
  }

  async function handleCheckIn(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedRoom) {
      showError('Vui lòng chọn phòng cần nhận khách.')
      return
    }
    if (selectedRoom.status !== 'TRONG_SACH') {
      showError(`Không thể nhận phòng ${selectedRoom.roomNumber}. Chỉ phòng Trống sạch mới được gán khi nhận phòng.`)
      return
    }
    if (!guestName.trim()) {
      showError('Vui lòng nhập tên khách.')
      return
    }

    setIsSaving(true)
    setNotice(null)
    try {
      if (!isDemoMode) {
        await checkIn(selectedRoom.id, guestName.trim())
      }
      setRooms((current) =>
        current.map((room) => (room.id === selectedRoom.id ? { ...room, status: 'DANG_O' } : room)),
      )
      setGuestName('')
      setNotice({ type: 'success', text: `Đã nhận phòng ${selectedRoom.roomNumber} cho khách ${guestName.trim()}.` })
    } catch (error) {
      showError(error instanceof Error ? error.message : 'Không thể thực hiện nhận phòng.')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">H</span>
          <div><strong>HomeStay</strong><small>Reception desk</small></div>
        </div>
        <div className="user-chip"><span>Lễ tân đang trực</span><span className="user-avatar">LT</span></div>
      </header>

      <main className="main-content">
        <section className="intro">
          <div>
            <p className="eyebrow">Vận hành phòng · S1-10</p>
            <h1>Phòng hôm nay,<br />sẵn sàng đón khách.</h1>
            <p>Theo dõi trạng thái phòng theo thời gian thực và chỉ gán phòng đã dọn sạch trong quy trình nhận phòng.</p>
          </div>
          <div className="live-pill"><span className="live-dot" /> Cập nhật trực tiếp</div>
        </section>

        <section className="summary-grid" aria-label="Tổng quan trạng thái phòng">
          <div className="summary-card clean"><span className="summary-label">Trống sạch</span><span className="summary-number">{counts.TRONG_SACH ?? 0}</span></div>
          <div className="summary-card dirty"><span className="summary-label">Trống bẩn</span><span className="summary-number">{counts.TRONG_BAN ?? 0}</span></div>
          <div className="summary-card occupied"><span className="summary-label">Đang ở</span><span className="summary-number">{counts.DANG_O ?? 0}</span></div>
          <div className="summary-card maintenance"><span className="summary-label">Bảo trì</span><span className="summary-number">{counts.BAO_TRI ?? 0}</span></div>
        </section>

        {notice && <div className={`alert ${notice.type === 'success' ? 'success' : ''}`} role="alert">{notice.text}</div>}

        <section className="workspace-grid">
          <div className="panel">
            <div className="panel-heading">
              <div><h2>Danh sách phòng</h2><p>Chọn trạng thái mới rồi lưu cho từng phòng.</p></div>
              <span className="room-floor">{rooms.length} phòng hoạt động</span>
            </div>
            {isLoading ? <div className="empty-state">Đang tải danh sách phòng...</div> : (
              <div className="room-grid">
                {rooms.map((room) => (
                  <article className="room-card" key={room.id}>
                    <div className="room-card-header">
                      <div><div className="room-number">Phòng {room.roomNumber}</div><div className="room-floor">Tầng {room.floor} · {room.roomType}</div></div>
                      <span className={`status-badge ${statusClassName[room.status]}`}>{ROOM_STATUS_LABELS[room.status]}</span>
                    </div>
                    <div className="room-meta">
                      <span>{ROOM_STATUS_HELP[room.status]}</span>
                      <select className="status-select" value={draftStatuses[room.id] ?? room.status}
                        onChange={(event) => setDraftStatuses((current) => ({ ...current, [room.id]: event.target.value as RoomStatus }))}
                        aria-label={`Trạng thái mới cho phòng ${room.roomNumber}`}>
                        {STATUS_OPTIONS.map((status) => <option value={status} key={status}>{ROOM_STATUS_LABELS[status]}</option>)}
                      </select>
                    </div>
                    <div className="room-actions">
                      <span className="room-type">Trạng thái hiện tại</span>
                      <button className="secondary-button" type="button" disabled={isSaving} onClick={() => handleStatusUpdate(room)}>Lưu trạng thái</button>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </div>

          <aside className="panel">
            <div className="panel-heading"><div><h2>Nhận phòng</h2><p>Chỉ chọn được phòng Trống sạch.</p></div></div>
            <form className="checkin-form" onSubmit={handleCheckIn}>
              <label className="form-label">Phòng gán cho khách
                <select className="form-control" value={selectedRoomId} onChange={(event) => setSelectedRoomId(event.target.value ? Number(event.target.value) : '')}>
                  <option value="">Chọn phòng</option>
                  {rooms.map((room) => <option value={room.id} key={room.id}>{room.roomNumber} · {ROOM_STATUS_LABELS[room.status]}</option>)}
                </select>
              </label>
              <label className="form-label">Tên khách
                <input className="form-control" value={guestName} onChange={(event) => setGuestName(event.target.value)} placeholder="Ví dụ: Nguyễn Minh Anh" />
              </label>
              {selectedRoom && selectedRoom.status !== 'TRONG_SACH' && <p className="checkin-note">Phòng đang là <strong>{ROOM_STATUS_LABELS[selectedRoom.status]}</strong>, không thể gán. Hãy chọn phòng Trống sạch.</p>}
              <button className="primary-button" type="submit" disabled={!canCheckIn || isSaving}>{isSaving ? 'Đang xử lý...' : 'Xác nhận nhận phòng'}</button>
            </form>
            {isDemoMode && <p className="demo-note">Đang ở chế độ demo vì backend chưa kết nối. Các thao tác vẫn mô phỏng đúng quy tắc nghiệp vụ; khi chạy backend, dữ liệu sẽ được lưu PostgreSQL.</p>}
          </aside>
        </section>
      </main>
    </div>
  )
}

export default App