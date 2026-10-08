import { useEffect, useMemo, useState, type FormEvent } from 'react'

import '../App.css'

import { hasPermission } from '../permissions/rolePermissions'
import { RoomShortageAlertSection } from '../components/RoomShortageAlertSection'



import {

  checkIn,

  checkOut,

  getRoomHistory,

  getRooms,

  putRoomIntoMaintenance,

  reportRoomIncident,

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
    hasGuestCheckInToday: false,
    expectedCheckInTime: null,
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
    hasGuestCheckInToday: false,
    expectedCheckInTime: null,
  },
  {
    id: 5,
    roomNumber: '105',
    floor: 1,
    roomType: 'Phòng đôi',
    status: 'TRONG_BAN',
    active: true,
    maintenanceReason: null,
    maintenanceStartDate: null,
    maintenanceEndDate: null,
    hasGuestCheckInToday: true,
    expectedCheckInTime: '14:00',
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
    hasGuestCheckInToday: false,
    expectedCheckInTime: null,
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
    hasGuestCheckInToday: false,
    expectedCheckInTime: null,
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



type RoomStatusPageProps = {
  role: string
  currentUser?: {
    fullName?: string
    email?: string
    role?: string
  }
}

type VisualIconProps = {
  type:
    | 'clean'
    | 'dirty'
    | 'occupied'
    | 'maintenance'
    | 'room'
    | 'checkin'
    | 'history'
    | 'save'
    | 'checkout'
    | 'check'
}



function VisualIcon({ type }: VisualIconProps) {

  const commonProps = {

    width: 20,

    height: 20,

    viewBox: '0 0 24 24',

    fill: 'none',

    stroke: 'currentColor',

    strokeWidth: 1.8,

    strokeLinecap: 'round' as const,

    strokeLinejoin: 'round' as const,

    'aria-hidden': true,

  }



  if (type === 'clean' || type === 'room') {

    return (

      <svg {...commonProps}>

        <path d="M3 18v-7a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v7" />

        <path d="M3 15h18" />

        <path d="M6 9V6h5a2 2 0 0 1 2 2v1" />

        <path d="M5 18v2" />

        <path d="M19 18v2" />

      </svg>

    )

  }



  if (type === 'dirty') {

    return (

      <svg {...commonProps}>

        <path d="M12 3v6" />

        <path d="M8.5 7.5 12 11l3.5-3.5" />

        <path d="M7 13h10l2 7H5l2-7Z" />

        <path d="M9 16h6" />

      </svg>

    )

  }



  if (type === 'occupied') {

    return (

      <svg {...commonProps}>

        <circle cx="9" cy="8" r="3" />

        <circle cx="17" cy="9" r="2.5" />

        <path d="M3 20v-2a5 5 0 0 1 5-5h2a5 5 0 0 1 5 5v2" />

        <path d="M15 14a4 4 0 0 1 6 3.5V20" />

      </svg>

    )

  }



  if (type === 'maintenance') {

    return (

      <svg {...commonProps}>

        <path d="m14.7 6.3 3-3" />

        <path d="m16 3 3 3" />

        <path d="m5 19 6.5-6.5" />

        <path d="M4 14 10 20" />

        <path d="m13 7 4 4" />

      </svg>

    )

  }



  if (type === 'checkin') {

    return (

      <svg {...commonProps}>

        <circle cx="12" cy="12" r="8" />

        <path d="M12 8v4l3 2" />

      </svg>

    )

  }



  if (type === 'history') {

    return (

      <svg {...commonProps}>

        <path d="M3 12a9 9 0 1 0 3-6.7" />

        <path d="M3 4v5h5" />

        <path d="M12 7v5l3 2" />

      </svg>

    )

  }



  if (type === 'save') {

    return (

      <svg {...commonProps}>

        <path d="M5 4h12l2 2v14H5V4Z" />

        <path d="M8 4v6h8V4" />

        <path d="M8 20v-6h8v6" />

      </svg>

    )

  }



  if (type === 'check') {
    return (
      <svg {...commonProps}>
        <polyline points="20 6 9 17 4 12" />
      </svg>
    )
  }

  return (
    <svg {...commonProps}>
      <path d="M9 6 3 12l6 6" />
      <path d="M3 12h12" />
      <path d="M15 5h5v14h-5" />
    </svg>
  )
}

export function RoomStatusPage({ role, currentUser }: RoomStatusPageProps) {
  const isHousekeeping = role === 'HOUSEKEEPING'
  const canCleanRoom = hasPermission(role, 'rooms:clean')
  const canChangeStatus = hasPermission(role, 'rooms:status:update') || canCleanRoom
  const canUseCheckIn = hasPermission(role, 'rooms:check-in')
  const canUseCheckOut = hasPermission(role, 'rooms:check-out')
  const canReportIncident = hasPermission(role, 'rooms:report-incident')

  const [rooms, setRooms] = useState<Room[]>([])
  const [cleaningRoomId, setCleaningRoomId] = useState<number | null>(null)

  // S3-09 AC4: Modal nhập ghi chú sự cố
  const [incidentModal, setIncidentModal] = useState<{
    roomId: number
    roomNumber: string
    note: string
    submitting: boolean
  } | null>(null)

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



  const visibleRooms = useMemo(() => {
    const list = isHousekeeping
      ? rooms.filter((room) => room.status === 'TRONG_BAN')
      : rooms

    return [...list].sort((a, b) => {
      // S3-09: Sắp xếp phòng có khách nhận trong ngày lên trước
      const aToday = Boolean(a.hasGuestCheckInToday)
      const bToday = Boolean(b.hasGuestCheckInToday)
      if (aToday !== bToday) {
        return aToday ? -1 : 1
      }
      if (a.expectedCheckInTime && b.expectedCheckInTime) {
        const timeDiff = a.expectedCheckInTime.localeCompare(b.expectedCheckInTime)
        if (timeDiff !== 0) return timeDiff
      }
      return a.roomNumber.localeCompare(b.roomNumber, undefined, { numeric: true })
    })
  }, [rooms, isHousekeeping])



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
        // S1-10 AC3: bắt buộc lý do và khoảng ngày dự kiến
        showError(
          `Vui lòng nhập lý do, ngày bắt đầu và ngày kết thúc dự kiến bảo trì cho phòng ${room.roomNumber}.`,
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
                  ? maintenance.endDate || null
                  : null,

            }

          : targetStatus === 'BAO_TRI'

            ? await putRoomIntoMaintenance(room.id, {
                ...maintenance,
                reason: maintenance.reason.trim(),
              endDate: maintenance.endDate || null,
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
            targetStatus === 'BAO_TRI'
              ? maintenance.endDate || null
              : null,

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

  async function handleMarkClean(room: Room) {
    if (cleaningRoomId === room.id || isSaving) {
      return
    }

    setCleaningRoomId(room.id)
    setIsSaving(true)
    setNotice(null)

    try {
      const savedRoom: Room = isDemoMode
        ? {
            ...room,
            status: 'TRONG_SACH',
          }
        : await updateRoomStatus(room.id, 'TRONG_SACH')

      // Cập nhật state danh sách phòng ngay lập tức (phòng biến mất khỏi danh sách phòng cần dọn của Housekeeping)
      setRooms((current) =>
        current.map((item) => (item.id === room.id ? savedRoom : item)),
      )

      const operator =
        currentUser?.fullName ||
        (isHousekeeping ? 'Nhân viên buồng phòng' : 'Lễ tân')

      const historyRecord: RoomStatusHistory = {
        id: Date.now(),
        roomId: room.id,
        roomNumber: room.roomNumber,
        previousStatus: room.status,
        newStatus: 'TRONG_SACH',
        changedBy: operator,
        changedAt: new Date().toISOString(),
      }

      setHistoryByRoom((current) => ({
        ...current,
        [room.id]: [
          ...(current[room.id] ?? DEMO_HISTORY[room.id] ?? []),
          historyRecord,
        ],
      }))

      if (!isDemoMode && selectedHistoryRoomId === room.id) {
        getRoomHistory(room.id)
          .then((hist) => {
            setHistoryByRoom((curr) => ({ ...curr, [room.id]: hist }))
          })
          .catch(() => {})
      }

      setNotice({
        type: 'success',
        text: `Đã báo phòng ${room.roomNumber} đã sạch thành công.`,
      })
    } catch (error) {
      showError(
        error instanceof Error
          ? error.message
          : 'Không thể báo phòng sạch. Vui lòng thử lại.',
      )
    } finally {
      setIsSaving(false)
      setCleaningRoomId(null)
    }
  }

  /** S3-09 AC4: Xử lý gửi báo sự cố từ modal */
  async function handleReportIncident(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!incidentModal) return

    const trimmedNote = incidentModal.note.trim()
    if (!trimmedNote) {
      setNotice({ type: 'error', text: 'Ghi chú sự cố không được để trống.' })
      return
    }

    setIncidentModal((prev) => prev && { ...prev, submitting: true })
    setNotice(null)

    try {
      const { roomId, roomNumber } = incidentModal

      const savedRoom: Room = isDemoMode
        ? { ...rooms.find((r) => r.id === roomId)!, status: 'BAO_TRI', maintenanceReason: trimmedNote }
        : await reportRoomIncident(roomId, trimmedNote)

      setRooms((current) =>
        current.map((item) => (item.id === roomId ? savedRoom : item)),
      )

      const operator =
        currentUser?.fullName ||
        (isHousekeeping ? 'Nhân viên buồng phòng' : 'Lễ tân')

      const historyRecord: RoomStatusHistory = {
        id: Date.now(),
        roomId,
        roomNumber,
        previousStatus: 'TRONG_BAN',
        newStatus: 'BAO_TRI',
        changedBy: operator,
        changedAt: new Date().toISOString(),
        maintenanceReason: trimmedNote,
      }

      setHistoryByRoom((current) => ({
        ...current,
        [roomId]: [
          ...(current[roomId] ?? DEMO_HISTORY[roomId] ?? []),
          historyRecord,
        ],
      }))

      if (!isDemoMode && selectedHistoryRoomId === roomId) {
        getRoomHistory(roomId)
          .then((hist) => {
            setHistoryByRoom((curr) => ({ ...curr, [roomId]: hist }))
          })
          .catch(() => {})
      }

      setIncidentModal(null)
      setNotice({
        type: 'success',
        text: `Đã báo sự cố phòng ${roomNumber}. Phòng chuyển sang bảo trì.`,
      })
    } catch (error) {
      setIncidentModal((prev) => prev && { ...prev, submitting: false })
      showError(
        error instanceof Error
          ? error.message
          : 'Không thể báo sự cố. Vui lòng thử lại.',
      )
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

    <div className="app-shell room-status-modern">

      <main className="main-content">

        {!isHousekeeping && <RoomShortageAlertSection />}

        {/* =========================

            TỔNG QUAN TRẠNG THÁI

           ========================= */}

        <section
          className="summary-grid"
          aria-label="Tổng quan trạng thái phòng"
        >
          {isHousekeeping ? (
            <div className="summary-card dirty">
              <div className="summary-icon">
                <VisualIcon type="dirty" />
              </div>

              <div className="summary-content">
                <span className="summary-label">Phòng cần dọn</span>
                <span className="summary-number">{counts.TRONG_BAN ?? 0}</span>
              </div>
            </div>
          ) : (
            <>
              <div className="summary-card clean">
                <div className="summary-icon">
                  <VisualIcon type="clean" />
                </div>

                <div className="summary-content">
                  <span className="summary-label">Trống sạch</span>
                  <span className="summary-number">{counts.TRONG_SACH ?? 0}</span>
                </div>
              </div>

              <div className="summary-card dirty">
                <div className="summary-icon">
                  <VisualIcon type="dirty" />
                </div>

                <div className="summary-content">
                  <span className="summary-label">Trống bẩn</span>
                  <span className="summary-number">{counts.TRONG_BAN ?? 0}</span>
                </div>
              </div>

              <div className="summary-card occupied">
                <div className="summary-icon">
                  <VisualIcon type="occupied" />
                </div>

                <div className="summary-content">
                  <span className="summary-label">Đang ở</span>
                  <span className="summary-number">{counts.DANG_O ?? 0}</span>
                </div>
              </div>

              <div className="summary-card maintenance">
                <div className="summary-icon">
                  <VisualIcon type="maintenance" />
                </div>

                <div className="summary-content">
                  <span className="summary-label">Bảo trì</span>
                  <span className="summary-number">{counts.BAO_TRI ?? 0}</span>
                </div>
              </div>
            </>
          )}
        </section>



        {/* =========================

            THÔNG BÁO

           ========================= */}

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



        {/* =========================

            NHẬN PHÒNG NHANH

           ========================= */}

        {canUseCheckIn && (

          <section className="quick-checkin-panel">

            <div className="quick-checkin-heading">

              <div className="quick-checkin-icon">
                <VisualIcon type="checkin" />
              </div>



              <div>

                <h2>Nhận phòng nhanh</h2>



                <p>

                  Chỉ phòng Trống sạch mới có thể nhận khách.

                </p>

              </div>

            </div>



            <form

              className="quick-checkin-form"

              onSubmit={handleCheckIn}

            >

              <label className="form-label">

                Phòng



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

                  <option value="">

                    Chọn phòng

                  </option>



                  {rooms.map((room) => (

                    <option

                      value={room.id}

                      key={room.id}

                    >

                      Phòng {room.roomNumber} ·{' '}

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



              <button

                className="primary-button quick-checkin-button"

                type="submit"

                disabled={!canCheckIn || isSaving}

              >

                {isSaving

                  ? 'Đang xử lý...'

                  : 'Xác nhận nhận phòng'}

              </button>

            </form>



            {selectedRoom &&

              selectedRoom.status !== 'TRONG_SACH' && (

                <p className="checkin-note">

                  Phòng đang là{' '}

                  <strong>

                    {

                      ROOM_STATUS_LABELS[

                        selectedRoom.status

                      ]

                    }

                  </strong>

                  , không thể nhận khách. Hãy chọn phòng Trống sạch.

                </p>

              )}



            {isDemoMode && (

              <p className="demo-note">

                Đang ở chế độ demo vì backend chưa kết nối.

                Thay đổi không được lưu vào PostgreSQL.

              </p>

            )}

          </section>

        )}



        {/* =========================

            DANH SÁCH PHÒNG

           ========================= */}

        <section className="rooms-section">

          <div className="rooms-section-header">

            <div>

              <span className="section-kicker">

                QUẢN LÝ TRẠNG THÁI

              </span>



              <h2>{isHousekeeping ? 'Danh sách phòng cần dọn' : 'Danh sách phòng'}</h2>

              <p>
                {isHousekeeping
                  ? 'Theo dõi phòng trống bẩn, ưu tiên phòng có khách nhận trong ngày.'
                  : 'Theo dõi và cập nhật trạng thái của từng phòng.'}
              </p>

            </div>



            <span className="room-count-badge">

              {isHousekeeping

                ? `${visibleRooms.length} phòng cần dọn`

                : `${rooms.length} phòng hoạt động`}

            </span>

          </div>



          {isLoading ? (

            <div className="empty-state">

              Đang tải danh sách phòng...

            </div>

          ) : visibleRooms.length === 0 ? (

            <div className="empty-state">

              {isHousekeeping

                ? 'Hiện không có phòng nào cần dọn.'

                : 'Chưa có phòng nào trong hệ thống.'}

            </div>

          ) : (

            <div className="modern-room-grid">

              {visibleRooms.map((room) => {

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

                    className="modern-room-card"

                    key={room.id}

                  >

                    <div className="modern-room-card-top">

                      <div className="room-identity">

                        <span className="room-icon">
                          <VisualIcon type="room" />
                        </span>



                        <div>

                          <div className="room-number">

                            Phòng {room.roomNumber}

                          </div>



                          <div className="room-floor">

                            Tầng {room.floor} ·{' '}

                            {room.roomType}

                          </div>

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

                    {/* S3-09: Thời gian nhận khách dự kiến & tag ưu tiên */}
                    <div className="room-checkin-info">
                      {room.hasGuestCheckInToday ? (
                        <div className="priority-banner">
                          <span className="priority-pill">⚡ Ưu tiên: Có khách nhận hôm nay</span>
                          <div className="checkin-time-row">
                            <span className="checkin-label">Thời gian nhận khách dự kiến:</span>
                            <span className="checkin-time-val">{room.expectedCheckInTime ?? '14:00'}</span>
                          </div>
                        </div>
                      ) : (
                        <div className="standard-checkin-banner">
                          <div className="checkin-time-row">
                            <span className="checkin-label">Thời gian nhận khách dự kiến:</span>
                            <span className="checkin-time-val empty">Chưa có khách đặt hôm nay</span>
                          </div>
                        </div>
                      )}
                    </div>

                    {!isHousekeeping && (
                      <div className="modern-room-status">
                        <span className="status-help">
                          {ROOM_STATUS_HELP[room.status]}
                        </span>

                        <label>
                          <span>Trạng thái</span>

                          <select
                            className="status-select"
                            disabled={!canChangeStatus}
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
                        </label>
                      </div>
                    )}



                    {currentDraftStatus === 'BAO_TRI' && (

                      <div className="maintenance-fields">

                        {room.status === 'DANG_O' && (

                          <div className="maintenance-warning">

                            ⚠ Phòng đang có khách ở. Cần hoàn tất

                            trả phòng trước khi chuyển sang bảo trì.

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

                            Ngày kết thúc dự kiến

                


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

                          <div className="maintenance-label-badge" style={{ fontSize: '0.82rem', fontWeight: 600, color: '#dc2626', marginBottom: '4px' }}>
                            ⚠️ Ghi chú sự cố / Bảo trì:
                          </div>

                          <strong className="maintenance-reason-text" style={{ color: '#991b1b', fontSize: '0.95rem' }}>

                            {room.maintenanceReason}

                          </strong>



                          <span>
                            {room.maintenanceStartDate ? `Từ: ${room.maintenanceStartDate}` : ''}
                            {room.maintenanceEndDate ? ` → Đến: ${room.maintenanceEndDate}` : ' (Chưa có ngày kết thúc)'}
                          </span>

                        </div>

                      )}



                    <div className="modern-room-actions">
                      <button
                        className="secondary-button"
                        type="button"
                        onClick={() => void handleViewHistory(room)}
                      >
                        <span className="button-icon">
                          <VisualIcon type="history" />
                        </span>
                        <span>
                          {selectedHistoryRoomId === room.id
                            ? 'Đóng lịch sử'
                            : 'Xem lịch sử'}
                        </span>
                      </button>

                      {/* S3-09: Thao tác 1 nút chuyển phòng từ trống bẩn sang trống sạch */}
                      {room.status === 'TRONG_BAN' && (isHousekeeping || canCleanRoom) && (
                        <button
                          className="primary-button mark-clean-button"
                          type="button"
                          disabled={isSaving || cleaningRoomId === room.id}
                          onClick={() => void handleMarkClean(room)}
                        >
                          <span className="button-icon">
                            <VisualIcon type="check" />
                          </span>
                          <span>
                            {cleaningRoomId === room.id
                              ? 'Đang xử lý...'
                              : 'Báo đã sạch'}
                          </span>
                        </button>
                      )}

                      {/* S3-09: Báo sự cố cho phòng trống bẩn */}
                      {room.status === 'TRONG_BAN' && (isHousekeeping || canReportIncident) && (
                        <button
                          className="secondary-button report-incident-button"
                          type="button"
                          disabled={isSaving || cleaningRoomId === room.id}
                          onClick={() =>
                            setIncidentModal({
                              roomId: room.id,
                              roomNumber: room.roomNumber,
                              note: '',
                              submitting: false,
                            })
                          }
                        >
                          <span className="button-icon">
                            <VisualIcon type="maintenance" />
                          </span>
                          <span>Báo sự cố</span>
                        </button>
                      )}

                      {canUseCheckOut && room.status === 'DANG_O' && (
                        <button
                          className="secondary-button checkout-button"
                          type="button"
                          disabled={isSaving}
                          onClick={() => void handleCheckOut(room)}
                        >
                          <span className="button-icon">
                            <VisualIcon type="checkout" />
                          </span>
                          <span>Trả phòng</span>
                        </button>
                      )}

                      {canChangeStatus && !isHousekeeping && (
                        <button
                          className="primary-button room-save-button"
                          type="button"
                          disabled={isSaving}
                          onClick={() => void handleStatusUpdate(room)}
                        >
                          <span className="button-icon">
                            <VisualIcon type="save" />
                          </span>
                          <span>Lưu trạng thái</span>
                        </button>
                      )}
                    </div>
                  </article>

                )

              })}

            </div>

          )}

        </section>



        {/* =========================

            LỊCH SỬ

           ========================= */}

        {selectedHistoryRoomId !== null && (

          <section className="history-panel">

            <div className="history-header">

              <div>

                <span className="section-kicker">

                  LỊCH SỬ HOẠT ĐỘNG

                </span>



                <h2>

                  Phòng{' '}

                  {rooms.find(

                    (room) =>

                      room.id === selectedHistoryRoomId,

                  )?.roomNumber ??

                    selectedHistoryRoomId}

                </h2>

              </div>



              <button

                type="button"

                className="secondary-button"

                onClick={() =>

                  setSelectedHistoryRoomId(null)

                }

              >

                ✕ Đóng

              </button>

            </div>



            {isHistoryLoading ? (

              <p>Đang tải lịch sử...</p>

            ) : (

              (

                historyByRoom[selectedHistoryRoomId] ?? []

              ).length === 0 ? (

                <p className="empty-state">

                  Chưa có lịch sử thay đổi trạng thái cho phòng này.

                </p>

              ) : (

                <ol className="history-list">

                  {(

                    historyByRoom[selectedHistoryRoomId] ?? []

                  ).map((item) => (

                    <li

                      className="history-item"

                      key={item.id}

                    >

                      <div className="history-item-top">

                        <strong>

                          {item.previousStatus &&

                          ROOM_STATUS_LABELS[item.previousStatus]

                            ? `${

                                ROOM_STATUS_LABELS[

                                  item.previousStatus

                                ]

                              } → `

                            : ''}



                          {ROOM_STATUS_LABELS[item.newStatus] ??

                            item.newStatus}

                        </strong>



                        <span className="history-item-time">

                          {new Date(

                            item.changedAt,

                          ).toLocaleString('vi-VN')}

                        </span>

                      </div>



                      <span>

                        Người thao tác: {item.changedBy}

                      </span>



                      {item.newStatus === 'BAO_TRI' &&

                        item.maintenanceReason && (

                          <span className="history-item-maintenance">

                            Lý do bảo trì:{' '}

                            {item.maintenanceReason} (

                            {item.maintenanceStartDate}

                            {' → '}

                            {item.maintenanceEndDate})

                          </span>

                        )}

                    </li>

                  ))}

                </ol>

              )

            )}

          </section>

        )}

        {/* S3-09 AC4: Modal nhập và gửi ghi chú sự cố */}
        {incidentModal && (
          <div
            className="incident-modal-backdrop"
            role="dialog"
            aria-modal="true"
            aria-labelledby="incident-modal-title"
          >
            <div className="incident-modal-card">
              <div className="incident-modal-header">
                <h3 id="incident-modal-title">
                  Báo sự cố - Phòng {incidentModal.roomNumber}
                </h3>
                <button
                  type="button"
                  className="incident-modal-close"
                  onClick={() => setIncidentModal(null)}
                  disabled={incidentModal.submitting}
                  aria-label="Đóng"
                >
                  ✕
                </button>
              </div>

              <form onSubmit={(e) => void handleReportIncident(e)}>
                <div className="incident-modal-body">
                  <div className="form-group">
                    <label htmlFor="incident-note-input">
                      Ghi chú về sự cố <span style={{ color: '#dc2626' }}>*</span>
                    </label>
                    <textarea
                      id="incident-note-input"
                      className="incident-textarea"
                      rows={4}
                      placeholder="Nhập chi tiết sự cố tại phòng (VD: Hỏng khóa cửa, hỏng điều hòa, sự cố đường ống nước...)"
                      value={incidentModal.note}
                      onChange={(e) =>
                        setIncidentModal((prev) =>
                          prev ? { ...prev, note: e.target.value } : null,
                        )
                      }
                      disabled={incidentModal.submitting}
                      required
                      autoFocus
                    />
                    <p className="incident-hint">
                      Phòng sẽ được chuyển sang trạng thái Bảo trì và ghi chú sự cố sẽ hiển thị cho Lễ tân.
                    </p>
                  </div>
                </div>

                <div className="incident-modal-footer">
                  <button
                    type="button"
                    className="secondary-button"
                    onClick={() => setIncidentModal(null)}
                    disabled={incidentModal.submitting}
                  >
                    Hủy
                  </button>
                  <button
                    type="submit"
                    className="submit-incident-btn"
                    disabled={incidentModal.submitting || !incidentModal.note.trim()}
                  >
                    {incidentModal.submitting ? 'Đang gửi...' : 'Gửi báo cáo sự cố'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

      </main>

    </div>

  )

}