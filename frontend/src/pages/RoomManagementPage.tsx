import {
  useCallback,
  useEffect,
  useMemo,
  useState,
} from 'react'

import '../App.css'
import './RoomManagementPage.css'

import type { RoomStatus } from '../types/room'
import type { RoomType } from '../types/roomType'
import { getRoomTypes } from '../services/roomTypeService'

import {
  createManagedRoom,
  getManagedRoomNote,
  searchManagedRooms,
  updateManagedRoom,
  type ManagedRoom,
} from '../services/roomManagementService'

const STATUS_OPTIONS: RoomStatus[] = [
  'TRONG_SACH',
  'TRONG_BAN',
  'DANG_O',
  'BAO_TRI',
]

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
  status: 'TRONG_SACH' as RoomStatus,
  // S1-07 AC1: phòng gồm số phòng, tầng, loại phòng, ghi chú và trạng thái hoạt động
  note: '',
}

/** S1-10 AC3: Bảo trì cần lý do và khoảng ngày nên chỉ chọn được ở màn hình Phòng, không chọn ở đây. */
const NON_MAINTENANCE_STATUS_OPTIONS = STATUS_OPTIONS.filter(
  (status) => status !== 'BAO_TRI',
)

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
  const [availableRoomTypes, setAvailableRoomTypes] = useState<RoomType[]>([])
  const [roomTypeFilter, setRoomTypeFilter] = useState('')
  const [floorFilter, setFloorFilter] = useState('')
  const [statusFilter, setStatusFilter] =
    useState<'' | RoomStatus>('')

  const [createForm, setCreateForm] =
    useState(emptyCreate)

  const [editRoom, setEditRoom] =
    useState<ManagedRoom | null>(null)

  const [editDraft, setEditDraft] =
    useState<EditDraft | null>(null)

  const [noteRoom, setNoteRoom] =
    useState<ManagedRoom | null>(null)

  const [roomNote, setRoomNote] = useState('')
  const [noteLoading, setNoteLoading] =
    useState(false)

  const [warning, setWarning] = useState<{
    message: string
    affected: number
  } | null>(null)

  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)

  const [notice, setNotice] = useState<{
    type: 'success' | 'error'
    text: string
  } | null>(null)

  const roomTypes = useMemo(
    () =>
      Array.from(
        new Set(
          rooms.map((room) => room.roomType),
        ),
      ).sort(),
    [rooms],
  )

  const floors = useMemo(
    () =>
      Array.from(
        new Set(
          rooms.map((room) => room.floor),
        ),
      ).sort((a, b) => a - b),
    [rooms],
  )

  const roomStats = useMemo(
    () => ({
      total: rooms.length,

      active: rooms.filter(
        (room) => room.active,
      ).length,

      maintenance: rooms.filter(
        (room) => room.status === 'BAO_TRI',
      ).length,
    }),
    [rooms],
  )

  const fetchRooms = useCallback(
    (
      filters: Parameters<
        typeof searchManagedRooms
      >[0],
    ) =>
      searchManagedRooms(filters)
        .then((result) => {
          setRooms(result.rooms)
        })
        .catch((error: unknown) => {
          setNotice({
            type: 'error',
            text:
              error instanceof Error
                ? error.message
                : 'Không thể tải danh sách phòng.',
          })
        })
        .finally(() => {
          setLoading(false)
        }),
    [],
  )

  function loadRooms() {
    setLoading(true)
    setNotice(null)

    return fetchRooms({
      roomType:
        roomTypeFilter || undefined,

      floor:
        floorFilter === ''
          ? undefined
          : Number(floorFilter),

      status:
        statusFilter || undefined,
    })
  }

  useEffect(() => {
    void fetchRooms({})
  }, [fetchRooms])
  useEffect(() => {
  void getRoomTypes()
    .then((items) => {
      setAvailableRoomTypes(items.filter((item) => item.active))
    })
    .catch(() => {
      setAvailableRoomTypes([])
    })
}, [])
  async function handleCreate(
    event: React.FormEvent,
  ) {
    event.preventDefault()

    setSaving(true)
    setNotice(null)

    try {
      const created =
        await createManagedRoom({
          roomNumber:
            createForm.roomNumber.trim(),

          floor:
            Number(createForm.floor),

          roomType:
            createForm.roomType.trim(),

            status:
            createForm.status,

          note:
            createForm.note.trim(),
        })

      setCreateForm(emptyCreate)

      setRooms((current) =>
        [created, ...current].sort(
          (a, b) =>
            a.roomNumber.localeCompare(
              b.roomNumber,
              undefined,
              {
                numeric: true,
              },
            ),
        ),
      )

      setNotice({
        type: 'success',
        text: `Đã tạo phòng ${created.roomNumber}.`,
      })
    } catch (error) {
      setNotice({
        type: 'error',
        text:
          error instanceof Error
            ? error.message
            : 'Không thể tạo phòng.',
      })
    } finally {
      setSaving(false)
    }
  }

  async function openEdit(
    room: ManagedRoom,
  ) {
    setNotice(null)
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
      const note =
        await getManagedRoomNote(
          room.id,
        )

      setEditDraft((current) =>
        current
          ? {
              ...current,
              note,
            }
          : current,
      )
    } catch {
      // Không tải được ghi chú vẫn cho phép sửa thông tin phòng.
    }
  }

  async function openNote(
    room: ManagedRoom,
  ) {
    setNoteRoom(room)
    setRoomNote('')
    setNoteLoading(true)

    try {
      const note =
        await getManagedRoomNote(
          room.id,
        )

      setRoomNote(note)
    } catch (error) {
      setRoomNote(
        error instanceof Error
          ? error.message
          : 'Không thể tải ghi chú phòng.',
      )
    } finally {
      setNoteLoading(false)
    }
  }

  function closeNote() {
    setNoteRoom(null)
    setRoomNote('')
    setNoteLoading(false)
  }

  function closeEdit() {
    setEditRoom(null)
    setEditDraft(null)
    setWarning(null)
  }

  async function submitUpdate(
    confirmWhenBookingCheckUnavailable: boolean,
  ) {
    if (!editRoom || !editDraft) {
      return
    }

    setSaving(true)
    setNotice(null)

    try {
      const result =
        await updateManagedRoom(
          editRoom.id,
          {
            ...editDraft,

            roomNumber:
              editDraft.roomNumber.trim(),

            roomType:
              editDraft.roomType.trim(),

            confirmWhenBookingCheckUnavailable,
          },
        )

      // Backend chỉ dừng lại chưa lưu khi warningRequired = true (cần xác nhận).
      // bookingCheckAvailable = false là thông tin, đã lưu xong thì không hiện "Cảnh báo trước khi lưu" nữa.
      if (
        !confirmWhenBookingCheckUnavailable &&
        result.warningRequired
      ) {
        setWarning({
          message:
            result.warningMessage,

          affected:
            result.affectedFutureBookings,
        })

        return
      }

      setWarning(null)

      setRooms((current) =>
        current.map((room) =>
          room.id === result.room.id
            ? result.room
            : room,
        ),
      )

      setNotice({
        type: 'success',
        text: `Đã cập nhật phòng ${result.room.roomNumber}.`,
      })

      closeEdit()
    } catch (error) {
      setNotice({
        type: 'error',
        text:
          error instanceof Error
            ? error.message
            : 'Không thể cập nhật phòng.',
      })
    } finally {
      setSaving(false)
    }
  }

  function clearFilters() {
    setRoomTypeFilter('')
    setFloorFilter('')
    setStatusFilter('')

    window.setTimeout(
      () => {
        setLoading(true)
        setNotice(null)

        void fetchRooms({})
      },
      0,
    )
  }

  return (
    <div className="room-management-page">
      <section
        className="management-summary"
        aria-label="Tổng quan phòng"
      >
        <article className="management-summary-card">
          <span className="management-summary-icon">
            ▦
          </span>

          <div>
            <span className="management-summary-label">
              Tổng phòng
            </span>

            <strong>
              {roomStats.total}
            </strong>
          </div>
        </article>

        <article className="management-summary-card">
          <span className="management-summary-icon">
            ✓
          </span>

          <div>
            <span className="management-summary-label">
              Đang hoạt động
            </span>

            <strong>
              {roomStats.active}
            </strong>
          </div>
        </article>

        <article className="management-summary-card">
          <span className="management-summary-icon">
            ⚒
          </span>

          <div>
            <span className="management-summary-label">
              Bảo trì
            </span>

            <strong>
              {roomStats.maintenance}
            </strong>
          </div>
        </article>
      </section>

      {notice && (
        <div
          className={`management-notice ${notice.type}`}
        >
          {notice.text}
        </div>
      )}

      <div className="management-grid">
        <section className="management-card">
          <div className="management-card-heading">
            <div>
              <span className="management-card-kicker">
                THÊM MỚI
              </span>

              <h2>Tạo phòng</h2>

              <p>
                Khởi tạo phòng mới và thiết lập
                trạng thái ban đầu.
              </p>
            </div>
          </div>

          <form
            className="management-form"
            onSubmit={handleCreate}
          >
            <label>
              Số phòng

              <input
                value={
                  createForm.roomNumber
                }
                onChange={(event) =>
                  setCreateForm({
                    ...createForm,
                    roomNumber:
                      event.target.value,
                  })
                }
                placeholder="Ví dụ: 301"
                required
              />
            </label>

            <label>
              Tầng

              <input
                type="number"
                min="0"
                value={createForm.floor}
                onChange={(event) =>
                  setCreateForm({
                    ...createForm,
                    floor:
                      Number(
                        event.target.value,
                      ),
                  })
                }
                required
              />
            </label>

           <label>
  Loại phòng

  <select
    value={createForm.roomType}
    onChange={(event) =>
      setCreateForm({
        ...createForm,
        roomType: event.target.value,
      })
    }
    required
  >
    <option value="">
      Chọn loại phòng
    </option>

    {availableRoomTypes.map((roomType) => (
      <option
        key={roomType.id}
        value={roomType.name}
      >
        {roomType.name}
      </option>
    ))}
  </select>
</label>

            <label>
              Trạng thái

              <select
                value={createForm.status}
                onChange={(event) =>
                  setCreateForm({
                    ...createForm,
                    status:
                      event.target
                        .value as RoomStatus,
                  })
                }
              >
                {NON_MAINTENANCE_STATUS_OPTIONS.map(
                  (status) => (
                    <option
                      key={status}
                      value={status}
                    >
                      {
                        STATUS_LABELS[
                          status
                        ]
                      }
                    </option>
                  ),
                )}
              </select>
            </label>
                        <label className="full-width">
              Ghi chú

              <textarea
                maxLength={500}
                value={createForm.note}
                onChange={(event) =>
                  setCreateForm({
                    ...createForm,
                    note: event.target.value,
                  })
                }
                placeholder="Ghi chú về phòng..."
              />
            </label>

            <button
              className="primary-button"
              disabled={saving}
              type="submit"
            >
              {saving
                ? 'Đang lưu...'
                : 'Tạo phòng'}
            </button>
          </form>
        </section>

        <section className="management-card">
          <div className="management-card-heading">
            <div>
              <span className="management-card-kicker">
                TRA CỨU
              </span>

              <h2>Tìm kiếm & lọc</h2>

              <p>
                Thu hẹp danh sách theo loại
                phòng, tầng và trạng thái.
              </p>
            </div>
          </div>

          <div className="management-filters">
            <label>
              Loại phòng

              <select
                value={roomTypeFilter}
                onChange={(event) =>
                  setRoomTypeFilter(
                    event.target.value,
                  )
                }
              >
                <option value="">
                  Tất cả
                </option>

                {roomTypes.map(
                  (type) => (
                    <option
                      key={type}
                      value={type}
                    >
                      {type}
                    </option>
                  ),
                )}
              </select>
            </label>

            <label>
              Tầng

              <select
                value={floorFilter}
                onChange={(event) =>
                  setFloorFilter(
                    event.target.value,
                  )
                }
              >
                <option value="">
                  Tất cả
                </option>

                {floors.map(
                  (floor) => (
                    <option
                      key={floor}
                      value={floor}
                    >
                      {floor}
                    </option>
                  ),
                )}
              </select>
            </label>

            <label>
              Trạng thái

              <select
                value={statusFilter}
                onChange={(event) =>
                  setStatusFilter(
                    event.target
                      .value as
                      | ''
                      | RoomStatus,
                  )
                }
              >
                <option value="">
                  Tất cả
                </option>

                {STATUS_OPTIONS.map(
                  (status) => (
                    <option
                      key={status}
                      value={status}
                    >
                      {
                        STATUS_LABELS[
                          status
                        ]
                      }
                    </option>
                  ),
                )}
              </select>
            </label>
          </div>

          <div className="filter-actions">
            <button
              className="primary-button"
              type="button"
              onClick={() =>
                void loadRooms()
              }
              disabled={loading}
            >
              {loading
                ? 'Đang tìm...'
                : 'Tìm kiếm'}
            </button>

            <button
              className="secondary-button"
              type="button"
              onClick={clearFilters}
            >
              Xóa bộ lọc
            </button>
          </div>
        </section>
      </div>

      <section className="management-card room-list-card">
        <div className="section-title-row">
          <div>
            <span className="management-card-kicker">
              DANH SÁCH
            </span>

            <h2>
              Danh sách phòng
            </h2>

            <p>
              Xem ghi chú hoặc chỉnh sửa
              thông tin của từng phòng.
            </p>
          </div>

          <span className="room-list-count">
            {rooms.length} phòng
          </span>
        </div>

        {rooms.length === 0 &&
        !loading ? (
          <div className="empty-result">
            Không tìm thấy phòng nào thỏa
            mãn điều kiện lọc.
          </div>
        ) : (
          <div className="managed-room-list">
            {rooms.map((room) => (
              <article
                className="managed-room"
                key={room.id}
              >
                <div>
                  <strong>
                    Phòng {room.roomNumber}
                  </strong>

                  <span>
                    Tầng {room.floor}
                    {' · '}
                    {room.roomType}
                  </span>
                </div>

                <span
                  className={`room-status-badge ${room.status.toLowerCase()}`}
                >
                  {
                    STATUS_LABELS[
                      room.status
                    ]
                  }
                </span>

                <div className="room-meta">
                  <span>
                    {room.active
                      ? 'Đang hoạt động'
                      : 'Ngừng hoạt động'}
                  </span>

                  <div className="room-card-actions">
                    <button
                      className="secondary-button"
                      type="button"
                      onClick={() =>
                        void openNote(room)
                      }
                    >
                      Ghi chú
                    </button>

                    <button
                      className="secondary-button"
                      type="button"
                      onClick={() =>
                        void openEdit(room)
                      }
                    >
                      Sửa thông tin
                    </button>
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>

      {noteRoom && (
        <div
          className="management-modal-backdrop"
          onMouseDown={closeNote}
        >
          <section
            className="management-modal room-note-modal"
            onMouseDown={(event) =>
              event.stopPropagation()
            }
          >
            <div className="section-title-row">
              <div>
                <span className="management-card-kicker">
                  GHI CHÚ
                </span>

                <h2>
                  Ghi chú phòng{' '}
                  {noteRoom.roomNumber}
                </h2>
              </div>

              <button
                className="modal-close"
                type="button"
                onClick={closeNote}
                aria-label="Đóng"
              >
                ×
              </button>
            </div>

            <div className="room-note-content">
              {noteLoading ? (
                <p>
                  Đang tải ghi chú...
                </p>
              ) : roomNote.trim() ? (
                <p>{roomNote}</p>
              ) : (
                <p className="room-note-empty">
                  Phòng này chưa có ghi chú.
                </p>
              )}
            </div>

            <div className="filter-actions">
              <button
                className="primary-button"
                type="button"
                onClick={closeNote}
              >
                Đóng
              </button>
            </div>
          </section>
        </div>
      )}

      {editRoom && editDraft && (
        <div
          className="management-modal-backdrop"
          onMouseDown={closeEdit}
        >
          <section
            className="management-modal"
            onMouseDown={(event) =>
              event.stopPropagation()
            }
          >
            <div className="section-title-row">
              <div>
                <h2>
                  Cập nhật phòng{' '}
                  {editRoom.roomNumber}
                </h2>
              </div>

              <button
                className="modal-close"
                type="button"
                onClick={closeEdit}
              >
                ×
              </button>
            </div>

            <div className="management-form two-columns">
              <label>
                Số phòng

                <input
                  value={
                    editDraft.roomNumber
                  }
                  onChange={(event) =>
                    setEditDraft({
                      ...editDraft,
                      roomNumber:
                        event.target.value,
                    })
                  }
                />
              </label>

              <label>
                Tầng

                <input
                  type="number"
                  min="0"
                  value={editDraft.floor}
                  onChange={(event) =>
                    setEditDraft({
                      ...editDraft,
                      floor:
                        Number(
                          event.target
                            .value,
                        ),
                    })
                  }
                />
              </label>

             <label>
  Loại phòng

  <select
    value={editDraft.roomType}
    onChange={(event) =>
      setEditDraft({
        ...editDraft,
        roomType: event.target.value,
      })
    }
  >
    {availableRoomTypes.map((roomType) => (
      <option
        key={roomType.id}
        value={roomType.name}
      >
        {roomType.name}
      </option>
    ))}
  </select>
</label>

              <label>
                Trạng thái

                <select
                  value={editDraft.status}
                  onChange={(event) =>
                    setEditDraft({
                      ...editDraft,
                      status:
                        event.target
                          .value as RoomStatus,
                    })
                  }
                >
                   {(editRoom?.status === 'BAO_TRI'
                    ? STATUS_OPTIONS
                    : NON_MAINTENANCE_STATUS_OPTIONS
                  ).map(
                    (status) => (
                      <option
                        key={status}
                        value={status}
                      >
                        {
                          STATUS_LABELS[
                            status
                          ]
                        }
                      </option>
                    ),
                  )}
                </select>
              </label>

              <label className="full-width">
                Ghi chú

                <textarea
                  maxLength={500}
                  value={editDraft.note}
                  onChange={(event) =>
                    setEditDraft({
                      ...editDraft,
                      note:
                        event.target.value,
                    })
                  }
                  placeholder="Ghi chú về phòng..."
                />
              </label>

              <label className="checkbox-row full-width">
                <input
                  type="checkbox"
                  checked={
                    editDraft.active
                  }
                  onChange={(event) =>
                    setEditDraft({
                      ...editDraft,
                      active:
                        event.target
                          .checked,
                    })
                  }
                />

                Phòng đang hoạt động
              </label>
            </div>

            {warning && (
              <div className="booking-warning">
                <strong>
                  ⚠ Cảnh báo trước khi lưu
                </strong>

                <p>
                  {warning.message}
                </p>

                {warning.affected >
                  0 && (
                  <p>
                    Có{' '}
                    {warning.affected}{' '}
                    booking tương lai có
                    thể bị ảnh hưởng.
                  </p>
                )}

                <div className="filter-actions">
                  <button
                    className="secondary-button"
                    type="button"
                    onClick={() =>
                      setWarning(null)
                    }
                  >
                    Hủy thay đổi
                  </button>

                  <button
                    className="primary-button"
                    type="button"
                    disabled={saving}
                    onClick={() =>
                      void submitUpdate(
                        true,
                      )
                    }
                  >
                    Xác nhận tiếp tục
                  </button>
                </div>
              </div>
            )}

            {!warning && (
              <div className="filter-actions">
                <button
                  className="secondary-button"
                  type="button"
                  onClick={closeEdit}
                >
                  Hủy
                </button>

                <button
                  className="primary-button"
                  type="button"
                  disabled={saving}
                  onClick={() =>
                    void submitUpdate(
                      false,
                    )
                  }
                >
                  {saving
                    ? 'Đang kiểm tra...'
                    : 'Lưu thay đổi'}
                </button>
              </div>
            )}
          </section>
        </div>
      )}
    </div>
  )
}