import { useEffect, useMemo, useState, type FormEvent } from 'react'
import '../App.css'

import {
  checkIn,
  checkOut,
  getRoomHistory,
  getRooms,
  putRoomIntoMaintenance,
  updateRoomStatus,
} from '../services/roomService'

import {
  ROOM_STATUS_HELP,
  ROOM_STATUS_LABELS,
  type MaintenanceDraft,
  type Room,
  type RoomStatus,
  type RoomStatusHistory,
} from '../types/room'

const STATUS_OPTIONS: RoomStatus[] = [
  'TRONG_SACH',
  'TRONG_BAN',
  'DANG_O',
  'BAO_TRI',
]

const DEMO_ROOMS: Room[] = [
  {
    id: 1,
    roomNumber: '101',
    floor: 1,
    roomType: 'Phòng đôi',
    status: 'TRONG_SACH',
    active: true,
    maintenanceReason: null,
    maintenanceStartDate: null,
    maintenanceEndDate: null,
  },
  {
    id: 2,
    roomNumber: '102',
    floor: 1,
    roomType: 'Phòng đôi',
    status: 'TRONG_BAN',
    active: true,
    maintenanceReason: null,
    maintenanceStartDate: null,
    maintenanceEndDate: null,
  },
  {
    id: 3,
    roomNumber: '201',
    floor: 2,
    roomType: 'Phòng gia đình',
    status: 'DANG_O',
    active: true,
    maintenanceReason: null,
    maintenanceStartDate: null,
    maintenanceEndDate: null,
  },
  {
    id: 4,
    roomNumber: '202',
    floor: 2,
    roomType: 'Phòng đơn',
    status: 'BAO_TRI',
    active: true,
    maintenanceReason: 'Sửa điều hòa',
    maintenanceStartDate: '2026-09-26',
    maintenanceEndDate: '2026-09-28',
  },
]

const DEMO_HISTORY: Record<number, RoomStatusHistory[]> = {
  1: [
    {
      id: 101,
      roomId: 1,
      roomNumber: '101',
      previousStatus: 'TRONG_BAN',
      newStatus: 'TRONG_SACH',
      changedBy: 'Lễ tân',
      changedAt: '2026-09-26T08:00:00+07:00',
    },
  ],
  2: [
    {
      id: 102,
      roomId: 2,
      roomNumber: '102',
      previousStatus: 'DANG_O',
      newStatus: 'TRONG_BAN',
      changedBy: 'Lễ tân',
      changedAt: '2026-09-26T09:30:00+07:00',
    },
  ],
  3: [
    {
      id: 103,
      roomId: 3,
      roomNumber: '201',
      previousStatus: 'TRONG_SACH',
      newStatus: 'DANG_O',
      changedBy: 'Lễ tân',
      changedAt: '2026-09-26T10:15:00+07:00',
    },
  ],
  4: [
    {
      id: 104,
      roomId: 4,
      roomNumber: '202',
      previousStatus: 'TRONG_BAN',
      newStatus: 'BAO_TRI',
      changedBy: 'Lễ tân',
      changedAt: '2026-09-26T11:00:00+07:00',
      maintenanceReason: 'Sửa điều hòa',
      maintenanceStartDate: '2026-09-26',
      maintenanceEndDate: '2026-09-28',
    },
  ],
}

const statusClassName: Record<RoomStatus, string> = {
  TRONG_SACH: 'clean',
  TRONG_BAN: 'dirty',
  DANG_O: 'occupied',
  BAO_TRI: 'maintenance',
}

export function RoomStatusPage() {
  const [rooms, setRooms] = useState<Room[]>([])
  const [draftStatuses, setDraftStatuses] = useState<
    Record<number, RoomStatus>
  >({})
  const [maintenanceDrafts, setMaintenanceDrafts] = useState<
    Record<number, MaintenanceDraft>
  >({})
  const [selectedRoomId, setSelectedRoomId] = useState<number | ''>('')
  const [guestName, setGuestName] = useState('')
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [notice, setNotice] = useState<{
    type: 'error' | 'success'
    text: string
  } | null>(null)
  const [isDemoMode, setIsDemoMode] = useState(false)

  // State quản lý lịch sử
  const [historyByRoom, setHistoryByRoom] = useState<
    Record<number, RoomStatusHistory[]>
  >({})
  const [selectedHistoryRoomId, setSelectedHistoryRoomId] = useState<
    number | null
  >(null)
  const [isHistoryLoading, setIsHistoryLoading] = useState(false)

  useEffect(() => {
    getRooms()
      .then((data) => {
        setRooms(data)

        setMaintenanceDrafts(
          Object.fromEntries(
            data.map((room) => [
              room.id,
              {
                reason: room.maintenanceReason ?? '',
                startDate: room.maintenanceStartDate ?? '',
                endDate: room.maintenanceEndDate ?? '',
              },
            ]),
          ),
        )

        if (data.length > 0) {
          const firstCleanRoom = data.find(
            (room) => room.status === 'TRONG_SACH',
          )

          setSelectedRoomId(firstCleanRoom?.id ?? data[0].id)
        }
      })
      .catch(() => {
        setRooms(DEMO_ROOMS)
        setHistoryByRoom(DEMO_HISTORY)

        setMaintenanceDrafts(
          Object.fromEntries(
            DEMO_ROOMS.map((room) => [
              room.id,
              {
                reason: room.maintenanceReason ?? '',
                startDate: room.maintenanceStartDate ?? '',
                endDate: room.maintenanceEndDate ?? '',
              },
            ]),
          ),
        )

        setSelectedRoomId(1)
        setIsDemoMode(true)
      })
      .finally(() => {
        setIsLoading(false)
      })
  }, [])

  const counts = useMemo(
    () =>
      STATUS_OPTIONS.reduce(
        (result, status) => {
          result[status] = rooms.filter(
            (room) => room.status === status,
          ).length

          return result
        },
        {} as Record<RoomStatus, number>,
      ),
    [rooms],
  )

  const selectedRoom = rooms.find(
    (room) => room.id === selectedRoomId,
  )

  const canCheckIn =
    selectedRoom?.status === 'TRONG_SACH' &&
    guestName.trim().length > 0

  function showError(text: string) {
    setNotice({
      type: 'error',
      text,
    })
  }

  async function handleViewHistory(room: Room) {
    if (selectedHistoryRoomId === room.id) {
      setSelectedHistoryRoomId(null)
      return
    }

    setSelectedHistoryRoomId(room.id)
    setNotice(null)

    if (isDemoMode) {
      if (!historyByRoom[room.id]) {
        setHistoryByRoom((current) => ({
          ...current,
          [room.id]: DEMO_HISTORY[room.id] ?? [],
        }))
      }
      return
    }

    setIsHistoryLoading(true)

    try {
      const history = await getRoomHistory(room.id)

      setHistoryByRoom((current) => ({
        ...current,
        [room.id]: history,
      }))
    } catch (error) {
      if (DEMO_HISTORY[room.id]) {
        setHistoryByRoom((current) => ({
          ...current,
          [room.id]: DEMO_HISTORY[room.id],
        }))
      } else {
        showError(
          error instanceof Error
            ? error.message
            : 'Không thể tải lịch sử phòng.',
        )
      }
    } finally {
      setIsHistoryLoading(false)
    }
  }

  async function handleStatusUpdate(room: Room) {
    const targetStatus =
      draftStatuses[room.id] ?? room.status

    if (targetStatus === room.status) {
      setNotice({
        type: 'success',
        text:
          `Phòng ${room.roomNumber} đã ở trạng thái ` +
          `${ROOM_STATUS_LABELS[room.status]}.`,
      })

      return
    }

    if (room.status === 'DANG_O' && targetStatus === 'BAO_TRI') {
      showError(
        `Không thể chuyển phòng ${room.roomNumber} đang ở sang bảo trì. Vui lòng hoàn tất trả phòng trước khi đưa phòng vào bảo trì.`,
      )
      return
    }

    const maintenance =
      maintenanceDrafts[room.id] ?? {
        reason: '',
        startDate: '',
        endDate: '',
      }

    if (targetStatus === 'BAO_TRI') {
      if (
        !maintenance.reason.trim() ||
        !maintenance.startDate ||
        !maintenance.endDate
      ) {
        showError(
          `Vui lòng nhập đầy đủ lý do, ngày bắt đầu và ` +
          `ngày kết thúc bảo trì cho phòng ${room.roomNumber}.`,
        )

        return
      }

      if (maintenance.endDate < maintenance.startDate) {
        showError(
          'Ngày kết thúc bảo trì phải từ ngày bắt đầu trở đi.',
        )

        return
      }
    }

    setIsSaving(true)
    setNotice(null)

    try {
      const savedRoom =
        isDemoMode
          ? {
              ...room,
              status: targetStatus,
              maintenanceReason:
                targetStatus === 'BAO_TRI'
                  ? maintenance.reason.trim()
                  : null,
              maintenanceStartDate:
                targetStatus === 'BAO_TRI'
                  ? maintenance.startDate
                  : null,
              maintenanceEndDate:
                targetStatus === 'BAO_TRI'
                  ? maintenance.endDate
                  : null,
            }
          : targetStatus === 'BAO_TRI'
            ? await putRoomIntoMaintenance(room.id, {
                ...maintenance,
                reason: maintenance.reason.trim(),
              })
            : await updateRoomStatus(room.id, targetStatus)

      setRooms((current) =>
        current.map((item) =>
          item.id === room.id ? savedRoom : item,
        ),
      )

      if (isDemoMode) {
        const historyRecord: RoomStatusHistory = {
          id: Date.now(),
          roomId: room.id,
          roomNumber: room.roomNumber,
          previousStatus: room.status,
          newStatus: targetStatus,
          changedBy: 'Lễ tân',
          changedAt: new Date().toISOString(),
          maintenanceReason:
            targetStatus === 'BAO_TRI' ? maintenance.reason.trim() : null,
          maintenanceStartDate:
            targetStatus === 'BAO_TRI' ? maintenance.startDate : null,
          maintenanceEndDate:
            targetStatus === 'BAO_TRI' ? maintenance.endDate : null,
        }
        setHistoryByRoom((current) => ({
          ...current,
          [room.id]: [
            ...(current[room.id] ?? DEMO_HISTORY[room.id] ?? []),
            historyRecord,
          ],
        }))
      }

      setNotice({
        type: 'success',
        text:
          `Đã cập nhật phòng ${room.roomNumber} sang ` +
          `${ROOM_STATUS_LABELS[targetStatus]}.`,
      })
    } catch (error) {
      showError(
        error instanceof Error
          ? error.message
          : 'Không thể cập nhật trạng thái phòng.',
      )
    } finally {
      setIsSaving(false)
    }
  }

  async function handleCheckIn(
    event: FormEvent<HTMLFormElement>,
  ) {
    event.preventDefault()

    if (!selectedRoom) {
      showError('Vui lòng chọn phòng cần nhận khách.')
      return
    }

    if (selectedRoom.status !== 'TRONG_SACH') {
      showError(
        `Không thể nhận phòng ${selectedRoom.roomNumber}. ` +
        'Chỉ phòng Trống sạch mới được gán khi nhận phòng.',
      )

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
        await checkIn(
          selectedRoom.id,
          guestName.trim(),
        )
      }

      setRooms((current) =>
        current.map((room) =>
          room.id === selectedRoom.id
            ? {
                ...room,
                status: 'DANG_O',
              }
            : room,
        ),
      )

      if (isDemoMode) {
        const historyRecord: RoomStatusHistory = {
          id: Date.now(),
          roomId: selectedRoom.id,
          roomNumber: selectedRoom.roomNumber,
          previousStatus: selectedRoom.status,
          newStatus: 'DANG_O',
          changedBy: 'Lễ tân',
          changedAt: new Date().toISOString(),
          maintenanceReason: null,
          maintenanceStartDate: null,
          maintenanceEndDate: null,
        }
        setHistoryByRoom((current) => ({
          ...current,
          [selectedRoom.id]: [
            ...(current[selectedRoom.id] ?? DEMO_HISTORY[selectedRoom.id] ?? []),
            historyRecord,
          ],
        }))
      }

      setGuestName('')

      setNotice({
        type: 'success',
        text:
          `Đã nhận phòng ${selectedRoom.roomNumber} ` +
          `cho khách ${guestName.trim()}.`,
      })
    } catch (error) {
      showError(
        error instanceof Error
          ? error.message
          : 'Không thể thực hiện nhận phòng.',
      )
    } finally {
      setIsSaving(false)
    }
  }

  async function handleCheckOut(room: Room) {
    if (room.status !== 'DANG_O') {
      showError(`Phòng ${room.roomNumber} không ở trạng thái Đang ở.`)
      return
    }

    setIsSaving(true)
    setNotice(null)

    try {
      const savedRoom = isDemoMode
        ? {
            ...room,
            status: 'TRONG_BAN' as RoomStatus,
            maintenanceReason: null,
            maintenanceStartDate: null,
            maintenanceEndDate: null,
          }
        : await checkOut(room.id)

      setRooms((current) =>
        current.map((item) => (item.id === room.id ? savedRoom : item)),
      )

      setDraftStatuses((current) => ({
        ...current,
        [room.id]: 'TRONG_BAN',
      }))

      if (isDemoMode) {
        const historyRecord: RoomStatusHistory = {
          id: Date.now(),
          roomId: room.id,
          roomNumber: room.roomNumber,
          previousStatus: 'DANG_O',
          newStatus: 'TRONG_BAN',
          changedBy: 'Lễ tân',
          changedAt: new Date().toISOString(),
          maintenanceReason: null,
          maintenanceStartDate: null,
          maintenanceEndDate: null,
        }
        setHistoryByRoom((current) => ({
          ...current,
          [room.id]: [
            ...(current[room.id] ?? DEMO_HISTORY[room.id] ?? []),
            historyRecord,
          ],
        }))
      }

      setNotice({
        type: 'success',
        text: `Đã trả phòng ${room.roomNumber} thành công. Trạng thái chuyển sang Trống bẩn.`,
      })
    } catch (error) {
      showError(
        error instanceof Error
          ? error.message
          : 'Không thể thực hiện trả phòng.',
      )
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <span className="brand-mark">H</span>

          <div>
            <strong>HomeStay</strong>
            <small>Reception desk</small>
          </div>
        </div>

        <div className="user-chip">
          <span>Lễ tân đang trực</span>
          <span className="user-avatar">LT</span>
        </div>
      </header>

      <main className="main-content">
        <section className="intro">
          <div>
            <p className="eyebrow">
              Vận hành phòng · S1-10
            </p>

            <h1>
              Phòng hôm nay,
              <br />
              sẵn sàng đón khách.
            </h1>

            <p>
              Theo dõi trạng thái phòng và quản lý
              thông tin bảo trì.
            </p>
          </div>

          <div className="live-pill">
            <span className="live-dot" />
            Cập nhật trực tiếp
          </div>
        </section>

        <section
          className="summary-grid"
          aria-label="Tổng quan trạng thái phòng"
        >
          <div className="summary-card clean">
            <span className="summary-label">
              Trống sạch
            </span>

            <span className="summary-number">
              {counts.TRONG_SACH ?? 0}
            </span>
          </div>

          <div className="summary-card dirty">
            <span className="summary-label">
              Trống bẩn
            </span>

            <span className="summary-number">
              {counts.TRONG_BAN ?? 0}
            </span>
          </div>

          <div className="summary-card occupied">
            <span className="summary-label">
              Đang ở
            </span>

            <span className="summary-number">
              {counts.DANG_O ?? 0}
            </span>
          </div>

          <div className="summary-card maintenance">
            <span className="summary-label">
              Bảo trì
            </span>

            <span className="summary-number">
              {counts.BAO_TRI ?? 0}
            </span>
          </div>
        </section>

        {notice && (
          <div
            className={`alert ${
              notice.type === 'success' ? 'success' : ''
            }`}
            role="alert"
          >
            {notice.text}
          </div>
        )}

        <section className="workspace-grid">
          <div className="panel">
            <div className="panel-heading">
              <div>
                <h2>Danh sách phòng</h2>

                <p>
                  Chọn trạng thái mới rồi lưu cho từng phòng.
                </p>
              </div>

              <span className="room-floor">
                {rooms.length} phòng hoạt động
              </span>
            </div>

            {isLoading ? (
              <div className="empty-state">
                Đang tải danh sách phòng...
              </div>
            ) : rooms.length === 0 ? (
              <div className="empty-state">
                Chưa có phòng nào trong hệ thống.
              </div>
            ) : (
              <div className="room-grid">
                {rooms.map((room) => {
                  const currentDraftStatus =
                    draftStatuses[room.id] ?? room.status

                  const maintenance =
                    maintenanceDrafts[room.id] ?? {
                      reason: room.maintenanceReason ?? '',
                      startDate:
                        room.maintenanceStartDate ?? '',
                      endDate:
                        room.maintenanceEndDate ?? '',
                    }

                  return (
                    <article
                      className="room-card"
                      key={room.id}
                    >
                      <div className="room-card-header">
                        <div>
                          <div className="room-number">
                            Phòng {room.roomNumber}
                          </div>

                          <div className="room-floor">
                            Tầng {room.floor} · {room.roomType}
                          </div>
                        </div>

                        <span
                          className={
                            `status-badge ${
                              statusClassName[room.status]
                            }`
                          }
                        >
                          {ROOM_STATUS_LABELS[room.status]}
                        </span>
                      </div>

                      <div className="room-meta">
                        <span>
                          {ROOM_STATUS_HELP[room.status]}
                        </span>

                        <select
                          className="status-select"
                          value={currentDraftStatus}
                          onChange={(event) => {
                            setNotice(null)

                            setDraftStatuses((current) => ({
                              ...current,
                              [room.id]:
                                event.target.value as RoomStatus,
                            }))
                          }}
                          aria-label={
                            `Trạng thái mới cho phòng ` +
                            `${room.roomNumber}`
                          }
                        >
                          {STATUS_OPTIONS.map((status) => (
                            <option
                              value={status}
                              key={status}
                            >
                              {ROOM_STATUS_LABELS[status]}
                            </option>
                          ))}
                        </select>
                      </div>

                      {currentDraftStatus === 'BAO_TRI' && (
                        <div className="maintenance-fields">
                          {room.status === 'DANG_O' && (
                            <div
                              style={{
                                background: '#fef3c7',
                                color: '#92400e',
                                padding: '8px 12px',
                                borderRadius: '8px',
                                fontSize: '12px',
                                marginBottom: '10px',
                                border: '1px solid #fde68a',
                              }}
                            >
                              ⚠️ Phòng đang có khách ở. Cần hoàn tất trả phòng trước khi chuyển sang bảo trì.
                            </div>
                          )}
                          <label className="form-label">
                            Lý do bảo trì

                            <input
                              className="form-control"
                              value={maintenance.reason}
                              maxLength={500}
                              placeholder="Ví dụ: Sửa điều hòa"
                              onChange={(event) =>
                                setMaintenanceDrafts(
                                  (current) => ({
                                    ...current,
                                    [room.id]: {
                                      ...maintenance,
                                      reason:
                                        event.target.value,
                                    },
                                  }),
                                )
                              }
                            />
                          </label>

                          <div className="maintenance-date-fields">
                            <label className="form-label">
                              Ngày bắt đầu

                              <input
                                className="form-control"
                                type="date"
                                value={maintenance.startDate}
                                onChange={(event) =>
                                  setMaintenanceDrafts(
                                    (current) => ({
                                      ...current,
                                      [room.id]: {
                                        ...maintenance,
                                        startDate:
                                          event.target.value,
                                      },
                                    }),
                                  )
                                }
                              />
                            </label>

                            <label className="form-label">
                              Ngày kết thúc

                              <input
                                className="form-control"
                                type="date"
                                min={
                                  maintenance.startDate ||
                                  undefined
                                }
                                value={maintenance.endDate}
                                onChange={(event) =>
                                  setMaintenanceDrafts(
                                    (current) => ({
                                      ...current,
                                      [room.id]: {
                                        ...maintenance,
                                        endDate:
                                          event.target.value,
                                      },
                                    }),
                                  )
                                }
                              />
                            </label>
                          </div>
                        </div>
                      )}

                      {room.status === 'BAO_TRI' &&
                        room.maintenanceReason && (
                          <div className="maintenance-summary">
                            <strong>
                              {room.maintenanceReason}
                            </strong>

                            <span>
                              {room.maintenanceStartDate}
                              {' → '}
                              {room.maintenanceEndDate}
                            </span>
                          </div>
                        )}

                      <div className="room-actions">
                        <button
                          className="secondary-button"
                          type="button"
                          onClick={() => void handleViewHistory(room)}
                        >
                          {selectedHistoryRoomId === room.id
                            ? '✕ Đóng lịch sử'
                            : 'Xem lịch sử'}
                        </button>

                        {room.status === 'DANG_O' && (
                          <button
                            className="secondary-button checkout-button"
                            type="button"
                            disabled={isSaving}
                            onClick={() => void handleCheckOut(room)}
                          >
                            Trả phòng
                          </button>
                        )}

                        <button
                          className="secondary-button"
                          type="button"
                          disabled={isSaving}
                          onClick={() =>
                            void handleStatusUpdate(room)
                          }
                        >
                          Lưu trạng thái
                        </button>
                      </div>
                    </article>
                  )
                })}
              </div>
            )}
          </div>

          <aside className="panel">
            <div className="panel-heading">
              <div>
                <h2>Nhận phòng</h2>

                <p>
                  Chỉ phòng Trống sạch mới được gán.
                </p>
              </div>
            </div>

            <form
              className="checkin-form"
              onSubmit={handleCheckIn}
            >
              <label className="form-label">
                Phòng gán cho khách

                <select
                  className="form-control"
                  value={selectedRoomId}
                  onChange={(event) =>
                    setSelectedRoomId(
                      event.target.value
                        ? Number(event.target.value)
                        : '',
                    )
                  }
                >
                  <option value="">Chọn phòng</option>

                  {rooms.map((room) => (
                    <option
                      value={room.id}
                      key={room.id}
                    >
                      {room.roomNumber} ·{' '}
                      {ROOM_STATUS_LABELS[room.status]}
                    </option>
                  ))}
                </select>
              </label>

              <label className="form-label">
                Tên khách

                <input
                  className="form-control"
                  value={guestName}
                  placeholder="Ví dụ: Nguyễn Minh Anh"
                  onChange={(event) =>
                    setGuestName(event.target.value)
                  }
                />
              </label>

              {selectedRoom &&
                selectedRoom.status !== 'TRONG_SACH' && (
                  <p className="checkin-note">
                    Phòng đang là{' '}
                    <strong>
                      {ROOM_STATUS_LABELS[
                        selectedRoom.status
                      ]}
                    </strong>
                    , không thể gán. Hãy chọn phòng Trống sạch.
                  </p>
                )}

              <button
                className="primary-button"
                type="submit"
                disabled={!canCheckIn || isSaving}
              >
                {isSaving
                  ? 'Đang xử lý...'
                  : 'Xác nhận nhận phòng'}
              </button>
            </form>

            {isDemoMode && (
              <p className="demo-note">
                Đang ở chế độ demo vì backend chưa kết nối.
                Thay đổi không được lưu vào PostgreSQL.
              </p>
            )}
          </aside>
        </section>

        {selectedHistoryRoomId !== null && (
          <section className="history-panel">
            <div className="history-header">
              <h2>
                Lịch sử chuyển đổi phòng{' '}
                {rooms.find((r) => r.id === selectedHistoryRoomId)?.roomNumber ??
                  selectedHistoryRoomId}
              </h2>
              <button
                type="button"
                className="secondary-button"
                onClick={() => setSelectedHistoryRoomId(null)}
              >
                ✕ Đóng
              </button>
            </div>

            {isHistoryLoading ? (
              <p>Đang tải lịch sử...</p>
            ) : (historyByRoom[selectedHistoryRoomId] ?? []).length === 0 ? (
              <p className="empty-state">
                Chưa có lịch sử thay đổi trạng thái cho phòng này.
              </p>
            ) : (
              <ol className="history-list">
                {(historyByRoom[selectedHistoryRoomId] ?? []).map((item) => (
                  <li className="history-item" key={item.id}>
                    <div className="history-item-top">
                      <strong>
                        {item.previousStatus && ROOM_STATUS_LABELS[item.previousStatus]
                          ? `${ROOM_STATUS_LABELS[item.previousStatus]} → `
                          : ''}
                        {ROOM_STATUS_LABELS[item.newStatus] ?? item.newStatus}
                      </strong>
                      <span className="history-item-time">
                        {new Date(item.changedAt).toLocaleString('vi-VN')}
                      </span>
                    </div>

                    <span>Người thao tác: {item.changedBy}</span>

                    {item.newStatus === 'BAO_TRI' && item.maintenanceReason && (
                      <span className="history-item-maintenance">
                        Lý do bảo trì: {item.maintenanceReason} ({item.maintenanceStartDate} → {item.maintenanceEndDate})
                      </span>

                    )}
                  </li>
                ))}
              </ol>
            )}
          </section>
        )}
      </main>
    </div>
  )
}

