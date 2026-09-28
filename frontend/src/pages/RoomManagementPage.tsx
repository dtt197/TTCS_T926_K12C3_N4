import { useCallback, useEffect, useMemo, useState } from 'react'
import '../App.css'
import './RoomManagementPage.css'
import type { RoomStatus } from '../types/room'
import { getRoomTypes } from '../services/roomTypeService'
import type { RoomType } from '../types/roomType'
import {
  createManagedRoom,
  getManagedRoomNote,
  searchManagedRooms,
  updateManagedRoom,
  type ManagedRoom,
} from '../services/roomManagementService'

const STATUS_OPTIONS: RoomStatus[] = ['TRONG_SACH', 'TRONG_BAN', 'DANG_O', 'BAO_TRI']
const STATUS_LABELS: Record<RoomStatus, string> = {
  TRONG_SACH: 'Trống sạch',
  TRONG_BAN: 'Trống bẩn',
  DANG_O: 'Đang ở',
  BAO_TRI: 'Bảo trì',
}

const emptyCreate = {
  roomNumber: '',
  floor: 1,
  roomType: '',
  note: '',
  active: true,
  status: 'TRONG_SACH' as RoomStatus,
}

type EditDraft = {
  roomNumber: string
  floor: number
  roomType: string
  status: RoomStatus
  active: boolean
  note: string
}

export function RoomManagementPage() {
  const [rooms, setRooms] = useState<ManagedRoom[]>([])
  const [roomTypeCatalog, setRoomTypeCatalog] = useState<RoomType[]>([])
  const [roomTypeFilter, setRoomTypeFilter] = useState('')
  const [floorFilter, setFloorFilter] = useState('')
  const [statusFilter, setStatusFilter] = useState<'' | RoomStatus>('')
  const [activeFilter, setActiveFilter] = useState<'' | 'true' | 'false'>('')
  const [createForm, setCreateForm] = useState(emptyCreate)
  const [editRoom, setEditRoom] = useState<ManagedRoom | null>(null)
  const [editDraft, setEditDraft] = useState<EditDraft | null>(null)
  const [editError, setEditError] = useState<string | null>(null)
  const [warning, setWarning] = useState<{ message: string; affected: number } | null>(null)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [notice, setNotice] = useState<{ type: 'success' | 'error'; text: string } | null>(null)

  const roomTypes = useMemo(
    () => Array.from(new Set(rooms.map((room) => room.roomType))).sort(),
    [rooms],
  )
  const floors = useMemo(
    () => Array.from(new Set(rooms.map((room) => room.floor))).sort((a, b) => a - b),
    [rooms],
  )
  const activeRoomTypes = useMemo(
    () => roomTypeCatalog.filter((roomType) => roomType.active),
    [roomTypeCatalog],
  )
  const editRoomTypes = useMemo(
    () => roomTypeCatalog.filter((roomType) => roomType.active || roomType.name === editDraft?.roomType),
    [editDraft?.roomType, roomTypeCatalog],
  )

  const loadRooms = useCallback((filters: {
    roomType?: string
    floor?: number
    status?: RoomStatus
    active?: boolean
  }) => searchManagedRooms(filters)
    .then((result) => setRooms(result.rooms))
    .catch((error: unknown) => {
      setNotice({ type: 'error', text: error instanceof Error ? error.message : 'Không thể tải danh sách phòng.' })
    })
    .finally(() => setLoading(false)), [])

  useEffect(() => {
    void loadRooms({})
    getRoomTypes()
      .then(setRoomTypeCatalog)
      .catch((error: unknown) => {
        setNotice({ type: 'error', text: error instanceof Error ? error.message : 'Không thể tải danh mục loại phòng.' })
      })
  }, [loadRooms])

  async function handleCreate(event: React.FormEvent) {
    event.preventDefault()
    setSaving(true)
    setNotice(null)
    try {
      const created = await createManagedRoom({
        roomNumber: createForm.roomNumber.trim(),
        floor: Number(createForm.floor),
        roomType: createForm.roomType.trim(),
        note: createForm.note.trim(),
        active: createForm.active,
        status: createForm.status,
      })
      setCreateForm(emptyCreate)
      setRooms((current) => [created, ...current].sort((a, b) => a.roomNumber.localeCompare(b.roomNumber, undefined, { numeric: true })))
      setNotice({ type: 'success', text: `Đã tạo phòng ${created.roomNumber}.` })
    } catch (error) {
      setNotice({ type: 'error', text: error instanceof Error ? error.message : 'Không thể tạo phòng.' })
    } finally {
      setSaving(false)
    }
  }

  async function openEdit(room: ManagedRoom) {
    setNotice(null)
    setEditError(null)
    setWarning(null)
    setEditRoom(room)
    setEditDraft({
      roomNumber: room.roomNumber,
      floor: room.floor,
      roomType: room.roomType,
      status: room.status,
      active: room.active,
      note: '',
    })
    try {
      const note = await getManagedRoomNote(room.id)
      setEditDraft((current) => (current ? { ...current, note } : current))
    } catch {
      // Ghi chú không tải được không ngăn người dùng sửa thông tin phòng.
    }
  }

  function closeEdit() {
    setEditRoom(null)
    setEditDraft(null)
    setEditError(null)
    setWarning(null)
  }

  function changeEditDraft(update: Partial<EditDraft>) {
    setEditDraft((current) => (current ? { ...current, ...update } : current))
    setEditError(null)
  }

  async function submitUpdate(confirmWhenBookingCheckUnavailable: boolean) {
    if (!editRoom || !editDraft) return
    setSaving(true)
    setNotice(null)
    setEditError(null)
    try {
      const result = await updateManagedRoom(editRoom.id, {
        ...editDraft,
        roomNumber: editDraft.roomNumber.trim(),
        roomType: editDraft.roomType.trim(),
        confirmWhenBookingCheckUnavailable,
      })

      if (!confirmWhenBookingCheckUnavailable
        && (result.warningRequired || (!result.bookingCheckAvailable && result.warningMessage))) {
        setWarning({ message: result.warningMessage, affected: result.affectedFutureBookings })
        return
      }

      setRooms((current) => current.map((room) => (room.id === result.room.id ? result.room : room)))
      setNotice({ type: 'success', text: `Đã cập nhật phòng ${result.room.roomNumber}.` })
      closeEdit()
    } catch (error) {
      setEditError(error instanceof Error ? error.message : 'Không thể cập nhật phòng.')
    } finally {
      setSaving(false)
    }
  }

  function clearFilters() {
    setRoomTypeFilter('')
    setFloorFilter('')
    setStatusFilter('')
    setActiveFilter('')
    setLoading(true)
    setNotice(null)
    void loadRooms({})
  }

  return (
    <div className="room-management-page">
      <section className="management-heading">
        <div>
          <h1>Quản lý phòng vật lý</h1>
          <p>Tạo phòng, tìm kiếm theo nhiều điều kiện và cập nhật thông tin phòng.</p>
        </div>
        <div className="management-count">{rooms.length} phòng</div>
      </section>

      {notice && <div className={`management-notice ${notice.type}`}>{notice.text}</div>}

      <div className="management-grid">
        <section className="management-card">
          <h2>Tạo phòng</h2>
          <form className="management-form" onSubmit={handleCreate}>
            <label>Số phòng<input value={createForm.roomNumber} onChange={(e) => setCreateForm({ ...createForm, roomNumber: e.target.value })} placeholder="Ví dụ: 301" required /></label>
            <label>Tầng<input type="number" min="0" value={createForm.floor} onChange={(e) => setCreateForm({ ...createForm, floor: Number(e.target.value) })} required /></label>
            <label>Loại phòng<select value={createForm.roomType} onChange={(e) => setCreateForm({ ...createForm, roomType: e.target.value })} required><option value="">Chọn loại phòng</option>{activeRoomTypes.map((roomType) => <option key={roomType.id} value={roomType.name}>{roomType.code} · {roomType.name}</option>)}</select></label>
            <label className="full-width">Ghi chú<textarea maxLength={500} value={createForm.note} onChange={(e) => setCreateForm({ ...createForm, note: e.target.value })} placeholder="Ghi chú về phòng..." /></label>
            <label>Trạng thái phòng<select value={createForm.status} onChange={(e) => setCreateForm({ ...createForm, status: e.target.value as RoomStatus })}>{STATUS_OPTIONS.map((status) => <option key={status} value={status}>{STATUS_LABELS[status]}</option>)}</select></label>
            <label className="checkbox-row"><input type="checkbox" checked={createForm.active} onChange={(e) => setCreateForm({ ...createForm, active: e.target.checked })} /> Phòng đang hoạt động</label>
            <button className="primary-button" disabled={saving || activeRoomTypes.length === 0} type="submit">{saving ? 'Đang lưu...' : 'Tạo phòng'}</button>
          </form>
        </section>

        <section className="management-card">
          <h2>Tìm kiếm & lọc</h2>
          <div className="management-filters">
            <label>Loại phòng<select value={roomTypeFilter} onChange={(e) => setRoomTypeFilter(e.target.value)}><option value="">Tất cả</option>{roomTypes.map((type) => <option key={type} value={type}>{type}</option>)}</select></label>
            <label>Tầng<select value={floorFilter} onChange={(e) => setFloorFilter(e.target.value)}><option value="">Tất cả</option>{floors.map((floor) => <option key={floor} value={floor}>{floor}</option>)}</select></label>
            <label>Trạng thái<select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as '' | RoomStatus)}><option value="">Tất cả</option>{STATUS_OPTIONS.map((status) => <option key={status} value={status}>{STATUS_LABELS[status]}</option>)}</select></label>
            <label>Trạng thái áp dụng<select value={activeFilter} onChange={(e) => setActiveFilter(e.target.value as '' | 'true' | 'false')}><option value="">Tất cả</option><option value="true">Đang áp dụng</option><option value="false">Ngừng áp dụng</option></select></label>
          </div>
          <div className="filter-actions">
            <button className="primary-button" type="button" onClick={() => {
              setLoading(true)
              setNotice(null)
              void loadRooms({
                roomType: roomTypeFilter || undefined,
                floor: floorFilter === '' ? undefined : Number(floorFilter),
                status: statusFilter || undefined,
                active: activeFilter === '' ? undefined : activeFilter === 'true',
              })
            }} disabled={loading}>{loading ? 'Đang tìm...' : 'Tìm kiếm'}</button>
            <button className="secondary-button" type="button" onClick={clearFilters}>Xóa bộ lọc</button>
          </div>
        </section>
      </div>

      <section className="management-card room-list-card">
        <div className="section-title-row"><div><h2>Danh sách phòng</h2><p>Chọn một phòng để sửa thông tin.</p></div></div>
        {rooms.length === 0 && !loading ? <div className="empty-result">Không tìm thấy phòng nào thỏa mãn điều kiện lọc.</div> : <div className="managed-room-list">{rooms.map((room) => <article className="managed-room" key={room.id}><div><strong>Phòng {room.roomNumber}</strong><span>Tầng {room.floor} · {room.roomType}</span></div><span className={`room-status-badge ${room.status.toLowerCase()}`}>{STATUS_LABELS[room.status]}</span><div className="room-meta"><span>{room.active ? 'Đang hoạt động' : 'Ngừng hoạt động'}</span><button className="secondary-button" type="button" onClick={() => void openEdit(room)}>Sửa thông tin</button></div></article>)}</div>}
      </section>

      {editRoom && editDraft && (
        <div className="management-modal-backdrop" onMouseDown={closeEdit}>
          <section className="management-modal" onMouseDown={(e) => e.stopPropagation()}>
            <div className="section-title-row"><div><h2>Cập nhật phòng {editRoom.roomNumber}</h2></div><button className="modal-close" type="button" onClick={closeEdit}>×</button></div>
            {editError && <div className="management-notice error" role="alert">{editError}</div>}
            <div className="management-form two-columns">
              <label>Số phòng<input value={editDraft.roomNumber} onChange={(e) => changeEditDraft({ roomNumber: e.target.value })} /></label>
              <label>Tầng<input type="number" min="0" value={editDraft.floor} onChange={(e) => changeEditDraft({ floor: Number(e.target.value) })} /></label>
              <label>Loại phòng<select value={editDraft.roomType} onChange={(e) => changeEditDraft({ roomType: e.target.value })} required><option value="" disabled>Chọn loại phòng</option>{editRoomTypes.map((roomType) => <option key={roomType.id} value={roomType.name}>{roomType.code} · {roomType.name}{roomType.active ? '' : ' (ngừng bán)'}</option>)}</select></label>
              <label>Trạng thái<select value={editDraft.status} onChange={(e) => changeEditDraft({ status: e.target.value as RoomStatus })}>{STATUS_OPTIONS.map((status) => <option key={status} value={status}>{STATUS_LABELS[status]}</option>)}</select></label>
              <label className="full-width">Ghi chú<textarea maxLength={500} value={editDraft.note} onChange={(e) => changeEditDraft({ note: e.target.value })} placeholder="Ghi chú về phòng..." /></label>
              <label className="checkbox-row full-width"><input type="checkbox" checked={editDraft.active} onChange={(e) => changeEditDraft({ active: e.target.checked })} /> Phòng đang hoạt động</label>
            </div>

            {warning && <div className="booking-warning"><strong>⚠ Cảnh báo trước khi lưu</strong><p>{warning.message}</p>{warning.affected > 0 && <p>Có {warning.affected} booking tương lai có thể bị ảnh hưởng.</p>}<div className="filter-actions"><button className="secondary-button" type="button" onClick={() => setWarning(null)}>Hủy thay đổi</button><button className="primary-button" type="button" disabled={saving} onClick={() => void submitUpdate(true)}>Xác nhận tiếp tục</button></div></div>}

            {!warning && <div className="filter-actions"><button className="secondary-button" type="button" onClick={closeEdit}>Hủy</button><button className="primary-button" type="button" disabled={saving} onClick={() => void submitUpdate(false)}>{saving ? 'Đang kiểm tra...' : 'Lưu thay đổi'}</button></div>}
          </section>
        </div>
      )}
    </div>
  )
}
