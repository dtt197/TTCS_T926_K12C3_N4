import { useCallback, useEffect, useMemo, useState, useRef, type FormEvent } from 'react'
import '../App.css'
import { hasPermission } from '../permissions/rolePermissions'
import { getCheckInOptions } from '../services/bookingService'
import { getEarlyCheckoutReasonError, getEarlyCheckoutSummary } from '../services/checkoutAc3'
import { completeCleaning, getMaintenanceCleaningRoomIds, getMaintenanceRequests } from '../services/maintenanceService'
import type { CheckInOption } from '../types/booking'
import {
  checkIn,
  checkOut,
  buildCheckoutPaymentRequest,
  getCheckoutPreview,
  getRoomHistory,
  getRooms,
  putRoomIntoMaintenance,
  reportRoomIncident,
  updateRoomStatus,
  type IncidentSeverity,
  type CheckoutPreview,
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
const checkoutMoney = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 4 })
function initialGuestCount(booking: CheckInOption | undefined) {
  return booking
    ? Math.min(Math.max(booking.requestedGuestCount ?? 1, 1), booking.maxGuests)
    : 1
}
function isBeforeStandardCheckIn() {
  const hour = Number(
    new Intl.DateTimeFormat('en-US', {
      timeZone: 'Asia/Ho_Chi_Minh',
      hour: '2-digit',
      hourCycle: 'h23',
    }).format(new Date()),
  )
  return hour < 14
}
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
const DEMO_CHECK_IN_OPTIONS: CheckInOption[] = [
  {
    bookingId: 101,
    bookingCode: 'BK-DEMO-101',
    guestName: 'Nguyễn Minh Anh',
    roomId: 1,
    roomNumber: '101',
    checkInDate: '2026-10-10',
    checkOutDate: '2026-10-12',
    requestedGuestCount: 2,
    maxGuests: 4,
  },
]
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
  const [cleaningMaintenanceIds, setCleaningMaintenanceIds] = useState<Record<number, number>>({})
  const [maintenanceCleaningNotes, setMaintenanceCleaningNotes] = useState<Record<number, string>>({})
  const [selectedStatusRoomId, setSelectedStatusRoomId] = useState<number | null>(null)
  const [openRoomActionsId, setOpenRoomActionsId] = useState<number | null>(null)
  const statusSaveLockRef = useRef(false)
  // S3-09 AC4: Modal nhập ghi chú sự cố
  const [incidentModal, setIncidentModal] = useState<{
    roomId: number
    roomNumber: string
    note: string
    severity: IncidentSeverity | ''
    submitting: boolean
    error: string | null
    retryRequest: { roomId: number; severity: IncidentSeverity; description: string; idempotencyKey: string } | null
  } | null>(null)
  const incidentSubmitLockRef = useRef(false)
  const [draftStatuses, setDraftStatuses] = useState<
    Record<number, RoomStatus>
  >({})
  const [maintenanceDrafts, setMaintenanceDrafts] = useState<
    Record<number, MaintenanceDraft>
  >({})
  const [checkInOptions, setCheckInOptions] = useState<CheckInOption[]>(DEMO_CHECK_IN_OPTIONS)
  const [selectedBookingId, setSelectedBookingId] = useState<number | ''>('')
  const [primaryGuestName, setPrimaryGuestName] = useState('')
  const [primaryGuestIdentityNumber, setPrimaryGuestIdentityNumber] = useState('')
  const [guestCount, setGuestCount] = useState(1)
  const [accompanyingGuestNames, setAccompanyingGuestNames] = useState<string[]>([])
  const [registeredGuests, setRegisteredGuests] = useState<
    { fullName: string; primary: boolean }[] | null
  >(null)
  const [earlyCheckInWarningOpen, setEarlyCheckInWarningOpen] = useState(false)
  const [isLoading, setIsLoading] = useState(true)
  const [isSaving, setIsSaving] = useState(false)
  const [checkoutDialog, setCheckoutDialog] = useState<{
    room: Room
    preview: CheckoutPreview
    paymentMethod: 'CASH' | 'BANK_TRANSFER'
    paymentReference: string
    confirmed: boolean
    earlyCheckoutReason: string
    earlyCheckoutReasonTouched: boolean
    error: string | null
  } | null>(null)
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
  const [historyError, setHistoryError] = useState<string | null>(null)
  useEffect(() => {
    if (selectedHistoryRoomId === null) return
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') setSelectedHistoryRoomId(null)
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [selectedHistoryRoomId])
  const closeStatusModal = useCallback(() => {
    if (statusSaveLockRef.current || isSaving) return
    setSelectedStatusRoomId(null)
  }, [isSaving])
  useEffect(() => {
    if (selectedStatusRoomId === null || isSaving) return
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') closeStatusModal()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [selectedStatusRoomId, isSaving, closeStatusModal])
  useEffect(() => {
    if (openRoomActionsId === null) return
    function onEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpenRoomActionsId(null)
    }
    function onPointerDown(event: PointerEvent) {
      if (!(event.target instanceof Element) ||
          !event.target.closest('[data-room-actions-menu]')) {
        setOpenRoomActionsId(null)
      }
    }
    document.addEventListener('keydown', onEscape)
    document.addEventListener('pointerdown', onPointerDown)
    return () => {
      document.removeEventListener('keydown', onEscape)
      document.removeEventListener('pointerdown', onPointerDown)
    }
  }, [openRoomActionsId])

  function openStatusModal(room: Room) {
    if (isSaving) return
    setNotice(null)
    setOpenRoomActionsId(null)
    setDraftStatuses((prev) => ({ ...prev, [room.id]: room.status }))
    setMaintenanceDrafts((prev) => ({
      ...prev,
      [room.id]: {
        reason: room.maintenanceReason ?? '',
        startDate: room.maintenanceStartDate ?? '',
        endDate: room.maintenanceEndDate ?? '',
      },
    }))
    setSelectedStatusRoomId(room.id)
  }

  async function saveStatusFromModal(room: Room) {
    if (statusSaveLockRef.current || isSaving) return
    statusSaveLockRef.current = true
    try {
      const saved = await handleStatusUpdate(room)
      if (saved) setSelectedStatusRoomId(null)
    } finally {
      statusSaveLockRef.current = false
    }
  }

  useEffect(() => {
       // Buồng phòng không có quyền xem booking (403), nên không gọi check-in-options,
    // nếu không cả trang sẽ rơi vào chế độ demo.
    const checkInOptionsRequest: Promise<CheckInOption[]> = isHousekeeping
      ? Promise.resolve([])
      : getCheckInOptions()
    Promise.all([getRooms(), checkInOptionsRequest, isHousekeeping ? Promise.all([getMaintenanceRequests(0), getMaintenanceCleaningRoomIds()]) : Promise.resolve(null)])
      .then(([data, options, housekeepingData]) => {
        const cleaningRoomIds = new Set(housekeepingData?.[1] ?? [])
        setRooms(isHousekeeping
          ? data.filter(room => room.status === 'TRONG_BAN' || cleaningRoomIds.has(room.id))
          : data)
        setCleaningMaintenanceIds(Object.fromEntries((housekeepingData?.[0].content ?? [])
          .filter((request) => request.status === 'CHO_VE_SINH')
          .map((request) => [request.roomId, request.id])))
        setCheckInOptions(options)
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
        const firstOption = options[0]
        setSelectedBookingId(firstOption?.bookingId ?? '')
        setPrimaryGuestName(firstOption?.guestName ?? '')
        setPrimaryGuestIdentityNumber('')
        const firstGuestCount = initialGuestCount(firstOption)
        setGuestCount(firstGuestCount)
        setAccompanyingGuestNames(Array.from({ length: firstGuestCount - 1 }, () => ''))
      })
      .catch(() => {
        setRooms(DEMO_ROOMS)
        setCheckInOptions(DEMO_CHECK_IN_OPTIONS)
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
        setSelectedBookingId(DEMO_CHECK_IN_OPTIONS[0].bookingId)
        setPrimaryGuestName(DEMO_CHECK_IN_OPTIONS[0].guestName)
        setPrimaryGuestIdentityNumber('')
        const firstGuestCount = initialGuestCount(DEMO_CHECK_IN_OPTIONS[0])
        setGuestCount(firstGuestCount)
        setAccompanyingGuestNames(Array.from({ length: firstGuestCount - 1 }, () => ''))
        setIsDemoMode(true) 
      })
      .finally(() => {
        setIsLoading(false)
      })
   }, [isHousekeeping])
  const visibleRooms = useMemo(() => {
    const list = isHousekeeping
      ? rooms.filter((room) => room.status === 'TRONG_BAN' || cleaningMaintenanceIds[room.id] !== undefined)
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
  }, [rooms, isHousekeeping, cleaningMaintenanceIds])
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
  const selectedBooking = checkInOptions.find(
    (booking) => booking.bookingId === selectedBookingId,
  )
  const selectedRoom = selectedBooking
    ? rooms.find((room) => room.id === selectedBooking.roomId)
    : undefined
  const canCheckIn =
    selectedRoom?.status === 'TRONG_SACH' &&
    selectedBooking !== undefined
  function showError(text: string) {
    setNotice({
      type: 'error',
      text,
    })
  }
async function loadRoomHistory(roomId: number) {
  setIsHistoryLoading(true)
  setHistoryError(null)
  try {
    const history = await getRoomHistory(roomId)
    setHistoryByRoom((current) => ({
      ...current,
      [roomId]: history,
    }))
  } catch (error) {
    setHistoryByRoom((current) => ({
      ...current,
      [roomId]: [],
    }))
    setHistoryError(
      error instanceof Error
        ? error.message
        : 'Không thể tải lịch sử phòng.',
    )
  } finally {
    setIsHistoryLoading(false)
  }
}
async function handleViewHistory(room: Room) {
  setSelectedHistoryRoomId(room.id)
  setNotice(null)
  setHistoryError(null)
  await loadRoomHistory(room.id)
}
  async function handleStatusUpdate(room: Room): Promise<boolean> {
    const targetStatus =
      draftStatuses[room.id] ?? room.status
    if (targetStatus === room.status) {
      setNotice({
        type: 'success',
        text:
          `Phòng ${room.roomNumber} đã ở trạng thái ` +
          `${ROOM_STATUS_LABELS[room.status]}.`,
      })
      return false
    }
    if (room.status === 'DANG_O' && targetStatus === 'BAO_TRI') {
      showError(
        `Không thể chuyển phòng ${room.roomNumber} đang ở sang bảo trì. Vui lòng hoàn tất trả phòng trước khi đưa phòng vào bảo trì.`,
      )
      return false
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
        return false
      }
        if (maintenance.endDate < maintenance.startDate) {
        showError(
          'Ngày kết thúc bảo trì phải từ ngày bắt đầu trở đi.',
        )
        return false
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
      window.dispatchEvent(new CustomEvent('room-status-changed'))
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
      return true
    } catch (error) {
      showError(
        error instanceof Error
          ? error.message
          : 'Không thể cập nhật trạng thái phòng.',
      )
      return false
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
      const maintenanceId = cleaningMaintenanceIds[room.id]
      if (maintenanceId !== undefined) {
        const notes = maintenanceCleaningNotes[room.id]?.trim()
        if (!notes) {
          setNotice({ type: 'error', text: 'Vui lòng nhập ghi chú vệ sinh sau bảo trì.' })
          return
        }
        await completeCleaning(maintenanceId, notes)
        const cleanedRoom = { ...room, status: 'TRONG_SACH' as const }
        setRooms(current => current.map(item => item.id === room.id ? cleanedRoom : item))
        setCleaningMaintenanceIds(current => {
          const next = { ...current }
          delete next[room.id]
          return next
        })
        setMaintenanceCleaningNotes(current => ({ ...current, [room.id]: '' }))
        window.dispatchEvent(new CustomEvent('room-status-changed'))
        setNotice({ type: 'success', text: `Đã vệ sinh và hoàn tất bảo trì phòng ${room.roomNumber}.` })
        return
      }
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
      window.dispatchEvent(new CustomEvent('room-status-changed'))
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
    if (!incidentModal || incidentSubmitLockRef.current || incidentModal.submitting) return
    const trimmedNote = incidentModal.note.trim()
    if (!trimmedNote) {
      setIncidentModal((prev) => prev && { ...prev, error: 'Vui lòng nhập mô tả sự cố.' })
      return
    }
    if (trimmedNote.length > 2000) {
      setIncidentModal((prev) => prev && { ...prev, error: 'Mô tả sự cố không được vượt quá 2000 ký tự.' })
      return
    }
    if (!incidentModal.severity) {
      setIncidentModal((prev) => prev && { ...prev, error: 'Vui lòng chọn mức độ sự cố.' })
      return
    }
    const { roomId, roomNumber, severity } = incidentModal
    const retry = incidentModal.retryRequest
    const request = retry
      && retry.roomId === roomId
      && retry.severity === severity
      && retry.description === trimmedNote
      ? retry
      : { roomId, severity, description: trimmedNote, idempotencyKey: crypto.randomUUID() }
    incidentSubmitLockRef.current = true
    setIncidentModal((prev) => prev && { ...prev, submitting: true, error: null, retryRequest: request })
    setNotice(null)
    try {
      const savedRoom: Room = isDemoMode
        ? { ...rooms.find((r) => r.id === roomId)!, status: 'BAO_TRI', maintenanceReason: trimmedNote }
        : await reportRoomIncident(request.roomId, request.severity, request.description, request.idempotencyKey)
      setRooms((current) =>
        current.map((item) => (item.id === roomId ? savedRoom : item)),
      )
      window.dispatchEvent(new CustomEvent('room-status-changed'))
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
      const errorWithStatus = error as Error & { status?: number }
      const isAmbiguousNetworkError = typeof errorWithStatus.status !== 'number'
      const isServerError = (errorWithStatus.status ?? 0) >= 500 && (errorWithStatus.status ?? 0) < 600
      const canRetrySameRequest = isAmbiguousNetworkError || isServerError
      const message = isAmbiguousNetworkError
        ? 'Không xác định được yêu cầu đã được ghi nhận hay chưa. Hãy thử lại để gửi cùng yêu cầu an toàn.'
        : isServerError
          ? 'Máy chủ gặp lỗi và chưa thể xác nhận kết quả. Hãy thử lại để gửi cùng yêu cầu an toàn.'
        : error instanceof Error ? error.message : 'Không thể báo sự cố.'
      const isNonRetryableStatus = [400, 403, 409].includes(errorWithStatus.status ?? 0)
      setIncidentModal((prev) => prev && {
        ...prev,
        submitting: false,
        error: canRetrySameRequest
          ? isAmbiguousNetworkError ? `${message} (Lỗi mạng)` : message
          : isNonRetryableStatus
            ? `${message} Hãy chỉnh thông tin hoặc bắt đầu báo cáo mới.`
            : message,
        retryRequest: canRetrySameRequest ? request : null,
      })
    } finally {
      incidentSubmitLockRef.current = false
    }
  }
  async function handleCheckIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedBooking || !selectedRoom) {
      showError('Vui lòng chọn booking đã xác nhận và có phòng được gán.')
      return
    }
    if (selectedRoom.status !== 'TRONG_SACH') {
      showError(
        `Không thể nhận phòng ${selectedRoom.roomNumber}. Chỉ phòng Trống sạch mới được nhận khách.`,
      )
      return
    }
    if (!primaryGuestName.trim()) {
      showError('Vui lòng nhập họ tên khách chính.')
      return
    }
    if (!/^[0-9]{12}$/.test(primaryGuestIdentityNumber)) {
      showError('Số CCCD khách chính phải gồm đúng 12 chữ số.')
      return
    }
    if (accompanyingGuestNames.some((name) => !name.trim())) {
      showError('Vui lòng nhập họ tên cho từng khách đi kèm.')
      return
    }
    if (guestCount !== accompanyingGuestNames.length + 1) {
      showError('Số lượng khách không khớp với danh sách khách đi kèm.')
      return
    }
    if (guestCount > selectedBooking.maxGuests) {
      showError(`Tổng số khách không được vượt quá ${selectedBooking.maxGuests} người.`)
      return
    }
    if (isBeforeStandardCheckIn()) {
      setEarlyCheckInWarningOpen(true)
      return
    }
    await submitCheckIn(false)
  }
  async function submitCheckIn(earlyCheckInConfirmed: boolean) {
    if (!selectedBooking || !selectedRoom) {
      showError('Vui lòng chọn booking đã xác nhận và có phòng được gán.')
      return
    }
    setIsSaving(true)
    setNotice(null)
    try {
      const result = isDemoMode
        ? {
            bookingId: selectedBooking.bookingId,
            bookingCode: selectedBooking.bookingCode,
            bookingStatus: 'DA_NHAN_PHONG' as const,
            roomStatus: 'DANG_O' as const,
            guestName: primaryGuestName.trim(),
            checkedInAt: new Date().toISOString(),
            registeredGuests: [
              { fullName: primaryGuestName.trim(), primary: true },
              ...accompanyingGuestNames.map((fullName) => ({
                fullName: fullName.trim(),
                primary: false,
              })),
            ],
          }
        : await checkIn(selectedRoom.id, selectedBooking.bookingId, {
            primaryGuestName: primaryGuestName.trim(),
            primaryGuestIdentityNumber,
            accompanyingGuestNames: accompanyingGuestNames.map((name) => name.trim()),
            earlyCheckInConfirmed,
          })
      setRooms((current) =>
        current.map((room) =>
          room.id === selectedRoom.id
            ? { ...room, status: result.roomStatus }
            : room,
        ),
      )
      setCheckInOptions((current) =>
        current.filter((booking) => booking.bookingId !== selectedBooking.bookingId),
      )
      setSelectedBookingId('')
      setRegisteredGuests(result.registeredGuests)
      if (isDemoMode) {
        const historyRecord: RoomStatusHistory = {
          id: Math.max(
            0,
            ...(historyByRoom[selectedRoom.id] ?? []).map((item) => item.id),
          ) + 1,
          roomId: selectedRoom.id,
          roomNumber: selectedRoom.roomNumber,
          previousStatus: selectedRoom.status,
          newStatus: result.roomStatus,
          changedBy: 'Lễ tân',
          changedAt: result.checkedInAt,
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
      const checkedInAt = new Date(result.checkedInAt).toLocaleString('vi-VN', {
        timeZone: 'Asia/Ho_Chi_Minh',
      })
      setNotice({
        type: 'success',
        text:
          `Đã nhận phòng booking ${result.bookingCode} cho ${result.guestName} tại phòng ${selectedRoom.roomNumber}. ` +
          'Booking: Đã nhận phòng; phòng: Đang ở. ' +
          `Thời điểm: ${checkedInAt} (Asia/Ho_Chi_Minh).`,
      })
      setEarlyCheckInWarningOpen(false)
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
      const preview: CheckoutPreview = isDemoMode
        ? {
            bookingId: room.id, bookingCode: `DEMO-${room.id}`, roomId: room.id,
            roomNumber: room.roomNumber, guestName: 'Khách demo', checkInDate: '', checkOutDate: '',
            nightlyPrices: [], nightlyBreakdownReliable: false, warning: 'Dữ liệu minh họa.',
            roomTotal: 0, surchargeAmount: 0, surchargeBreakdownComplete: false,
            extraGuestCount: null, extraPersonFeeSnapshot: null, surchargeNights: null,
            roomTotalExcludingSurcharge: null, surchargeBreakdownWarning: null,
            bookingSurcharges: [], bookingSurchargeTotal: 0,
            depositTotal: 0, checkoutTimeSnapshot: '12:00:00', serverCheckoutTime: new Date().toISOString(),
            operatingSettingsId: 0, lateCheckoutHours: 0, lateCheckoutFeePerHour: 0,
            lateCheckoutFee: 0, settlementTotalBeforeDeposit: 0, lateCheckoutPricingBasis: 'NONE',
            checkoutAllowed: true, checkoutBlockedReason: null,
            settlementType: 'ZERO', amount: 0, previewToken: 'demo',
            earlyCheckout: false, bookedNights: 0, usedNights: 0, unusedNights: 0,
            unusedRoomValue: null, unusedRoomValueWarning: null,
          }
        : await getCheckoutPreview(room.id)
      setCheckoutDialog({ room, preview, paymentMethod: 'CASH', paymentReference: '', confirmed: false, earlyCheckoutReason: '', earlyCheckoutReasonTouched: false, error: null })
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

  async function reloadCheckoutPreview() {
    if (!checkoutDialog) return
    try {
      const preview = await getCheckoutPreview(checkoutDialog.room.id)
      setCheckoutDialog((current) => current ? { ...current, preview, confirmed: false, earlyCheckoutReason: '', earlyCheckoutReasonTouched: false, error: null } : current)
    } catch (error) {
      setCheckoutDialog((current) => current ? {
        ...current,
        error: error instanceof Error ? error.message : 'Không thể tải lại bảng quyết toán.',
      } : current)
    }
  }

  async function confirmCheckout() {
    if (!checkoutDialog) return
    const { room, preview } = checkoutDialog
    const reasonError = getEarlyCheckoutReasonError(preview.earlyCheckout, checkoutDialog.earlyCheckoutReason)
    if (reasonError) {
      setCheckoutDialog((current) => current ? { ...current, error: reasonError } : current)
      return
    }
    setIsSaving(true)
    try {
      const savedRoom = isDemoMode
        ? { ...room, status: 'TRONG_BAN' as RoomStatus, maintenanceReason: null, maintenanceStartDate: null, maintenanceEndDate: null }
        : await checkOut(room.id, buildCheckoutPaymentRequest(preview, {
              paymentMethod: checkoutDialog.paymentMethod,
              paymentReference: checkoutDialog.paymentReference,
              amountReceivedConfirmed: preview.settlementType === 'COLLECT' ? checkoutDialog.confirmed : undefined,
              refundCompletedConfirmed: preview.settlementType === 'REFUND' ? checkoutDialog.confirmed : undefined,
            }, checkoutDialog.earlyCheckoutReason))
      setRooms((current) => current.map((item) => item.id === room.id ? savedRoom : item))
      window.dispatchEvent(new CustomEvent('room-status-changed'))
      setDraftStatuses((current) => ({ ...current, [room.id]: 'TRONG_BAN' }))
      setCheckoutDialog(null)
      setNotice({ type: 'success', text: `Đã quyết toán và trả phòng ${room.roomNumber}. Trạng thái phòng chuyển sang Trống bẩn.` })
    } catch (error) {
      setCheckoutDialog((current) => current ? {
        ...current,
        error: error instanceof Error ? error.message : 'Không thể hoàn tất quyết toán.',
      } : current)
    } finally {
      setIsSaving(false)
    }
  }
  const earlyCheckoutSummary = checkoutDialog?.preview.earlyCheckout
    ? getEarlyCheckoutSummary(checkoutDialog.preview)
    : null

  return (
    <div className="app-shell room-status-modern">
      <main className="main-content">
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
                <p>Chọn booking đã xác nhận, đã gán phòng Trống sạch.</p>
              </div>
            </div>
            <form
              className="quick-checkin-form"
              onSubmit={handleCheckIn}
              noValidate
            >
              <label className="form-label">
                Booking
                <select
                  className="form-control"
                  value={selectedBookingId}
                  onChange={(event) => {
                    const nextBookingId = event.target.value ? Number(event.target.value) : ''
                    const nextBooking = checkInOptions.find(
                      (booking) => booking.bookingId === nextBookingId,
                    )
                    setSelectedBookingId(nextBookingId)
                    setPrimaryGuestName(nextBooking?.guestName ?? '')
                    setPrimaryGuestIdentityNumber('')
                    const nextGuestCount = initialGuestCount(nextBooking)
                    setGuestCount(nextGuestCount)
                    setAccompanyingGuestNames(
                      Array.from({ length: nextGuestCount - 1 }, () => ''),
                    )
                    setRegisteredGuests(null)
                  }}
                >
                  <option value="">
                    Chọn booking
                  </option>
                  {checkInOptions.map((booking) => (
                    <option
                      value={booking.bookingId}
                      key={booking.bookingId}
                    >
                      {booking.bookingCode} · {booking.guestName} · Phòng {booking.roomNumber}
                    </option>
                  ))}
                </select>
              </label>
              <label className="form-label">
                Khách và phòng
                <span className="form-control" aria-live="polite">
                  {selectedBooking
                    ? `${selectedBooking.guestName} · Phòng ${selectedBooking.roomNumber}`
                    : 'Không có booking đủ điều kiện nhận phòng'}
                </span>
              </label>
              <label className="form-label">
                Họ tên khách chính
                <input
                  className="form-control"
                  value={primaryGuestName}
                  onChange={(event) => setPrimaryGuestName(event.target.value)}
                  maxLength={120}
                  required
                />
              </label>
              <label className="form-label">
                Số CCCD khách chính
                <input
                  className="form-control"
                  value={primaryGuestIdentityNumber}
                  onChange={(event) => setPrimaryGuestIdentityNumber(event.target.value)}
                  inputMode="numeric"
                  pattern="[0-9]{12}"
                  maxLength={12}
                  required
                />
              </label>
              <label className="form-label">
                Tổng số khách (tối đa {selectedBooking?.maxGuests ?? 0})
                <select
                  className="form-control"
                  value={guestCount}
                  onChange={(event) => {
                    const nextCount = Number(event.target.value)
                    setGuestCount(nextCount)
                    setAccompanyingGuestNames((current) =>
                      Array.from(
                        { length: nextCount - 1 },
                        (_, index) => current[index] ?? '',
                      ),
                    )
                  }}
                  disabled={!selectedBooking}
                >
                  {Array.from({ length: selectedBooking?.maxGuests ?? 1 }, (_, index) => index + 1)
                    .map((count) => (
                      <option value={count} key={count}>{count}</option>
                    ))}
                </select>
              </label>
              {accompanyingGuestNames.map((name, index) => (
                <label className="form-label" key={index}>
                  Họ tên khách đi kèm {index + 1}
                  <input
                    className="form-control"
                    value={name}
                    onChange={(event) =>
                      setAccompanyingGuestNames((current) =>
                        current.map((guestName, guestIndex) =>
                          guestIndex === index ? event.target.value : guestName,
                        ),
                      )
                    }
                    maxLength={120}
                    required
                  />
                </label>
              ))}
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
            {registeredGuests && (
              <div className="checkin-note" aria-live="polite">
                <strong>Khách đã đăng ký lưu trú:</strong>
                <ul>
                  {registeredGuests.map((guest, index) => (
                    <li key={`${guest.primary ? 'primary' : 'guest'}-${index}`}>
                      {guest.fullName}{guest.primary ? ' (khách chính)' : ''}
                    </li>
                  ))}
                </ul>
              </div>
            )}
            {checkInOptions.length === 0 && (
                <p className="checkin-note">
                  Hiện không có booking đủ điều kiện để nhận phòng.
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
                const actions = [
                  ...((room.status === 'TRONG_BAN' || cleaningMaintenanceIds[room.id] !== undefined) && (isHousekeeping || canCleanRoom) ? [{
                    id: 'clean', label: cleaningRoomId === room.id ? 'Đang xử lý...' : 'Báo đã sạch',
                    icon: 'check' as const, disabled: isSaving || cleaningRoomId === room.id,
                    run: () => void handleMarkClean(room),
                  }] : []),
                  ...(room.status === 'TRONG_BAN' && cleaningMaintenanceIds[room.id] === undefined && (isHousekeeping || canReportIncident) ? [{
                    id: 'incident', label: 'Báo sự cố', icon: 'maintenance' as const,
                    disabled: isSaving || cleaningRoomId === room.id,
                    run: () => setIncidentModal({ roomId: room.id, roomNumber: room.roomNumber, note: '', severity: '', submitting: false, error: null, retryRequest: null }),
                  }] : []),
                  ...(canUseCheckOut && room.status === 'DANG_O' ? [{
                    id: 'checkout', label: 'Trả phòng', icon: 'checkout' as const,
                    disabled: isSaving, run: () => void handleCheckOut(room),
                  }] : []),
                  ...(canChangeStatus && !isHousekeeping ? [{
                    id: 'status', label: 'Cập nhật trạng thái', icon: 'save' as const,
                    disabled: isSaving, run: () => openStatusModal(room),
                  }] : []),
                ]
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
                    <div className="room-card-spacer" aria-hidden="true" />
                    {cleaningMaintenanceIds[room.id] !== undefined && (
                      <label className="maintenance-cleaning-notes">
                        Ghi chú vệ sinh sau bảo trì
                        <textarea required maxLength={2000} value={maintenanceCleaningNotes[room.id] ?? ''}
                          onChange={event => setMaintenanceCleaningNotes(current => ({ ...current, [room.id]: event.target.value }))} />
                      </label>
                    )}
                    <div className="modern-room-actions">
                      <button className="secondary-button" type="button"
                        onClick={() => void handleViewHistory(room)}>
                        <span className="button-icon"><VisualIcon type="history" /></span>
                        <span>Xem lịch sử</span>
                      </button>
                      {actions.length === 1 && (
                        <button className="primary-button room-primary-action" type="button"
                          disabled={actions[0].disabled} onClick={actions[0].run}>
                          <span className="button-icon"><VisualIcon type={actions[0].icon} /></span>
                          <span>{actions[0].label}</span>
                        </button>
                      )}
                      {actions.length > 1 && (
                        <div className="room-actions-menu" data-room-actions-menu>
                          <button className="primary-button room-primary-action" type="button"
                            aria-expanded={openRoomActionsId === room.id}
                            aria-haspopup="true"
                            onClick={() => setOpenRoomActionsId((id) => id === room.id ? null : room.id)}>
                            Thao tác ▾
                          </button>
                          {openRoomActionsId === room.id && (
                            <div className="room-actions-dropdown">
                              {actions.map((action) => (
                                <button key={action.id} type="button" disabled={action.disabled}
                                  onClick={() => { setOpenRoomActionsId(null); action.run() }}>
                                  <VisualIcon type={action.icon} /><span>{action.label}</span>
                                </button>
                              ))}
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  </article>
                )
              })}
            </div>
          )}
        </section>
        {selectedStatusRoomId !== null && (() => {
          const room = rooms.find((item) => item.id === selectedStatusRoomId)
          if (!room) return null
          const currentDraftStatus = draftStatuses[room.id] ?? room.status
          const maintenance = maintenanceDrafts[room.id] ?? {
            reason: room.maintenanceReason ?? '',
            startDate: room.maintenanceStartDate ?? '',
            endDate: room.maintenanceEndDate ?? '',
          }
          return (
            <div className="room-status-modal-backdrop"
              onMouseDown={(event) => {
                if (event.target === event.currentTarget) closeStatusModal()
              }}>
              <section className="room-status-modal" role="dialog" aria-modal="true"
                aria-labelledby="room-status-modal-title">
                <header className="room-status-modal-header">
                  <div>
                    <span className="section-kicker">CẬP NHẬT TRẠNG THÁI</span>
                    <h2 id="room-status-modal-title">Phòng {room.roomNumber}</h2>
                    <p>{room.roomType} · Hiện tại: {ROOM_STATUS_LABELS[room.status]}</p>
                  </div>
                  <button className="room-history-modal-close" type="button"
                    aria-label="Đóng" disabled={isSaving}
                    onClick={closeStatusModal}>✕</button>
                </header>
                <div className="room-status-modal-body">
                  {notice?.type === 'error' && <div className="alert" role="alert">{notice.text}</div>}
                    <div className="modern-room-status">
                        <span className="status-help">
                          {ROOM_STATUS_HELP[room.status]}
                        </span>
                        <label>
                          <span>Trạng thái</span>
                          <select
                            className="status-select"
                            disabled={!canChangeStatus || isSaving}
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
                            disabled={isSaving}
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
                              disabled={isSaving}
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
                              disabled={isSaving}
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
                </div>
                <footer className="room-status-modal-footer">
                  <button className="secondary-button" type="button" disabled={isSaving}
                    onClick={closeStatusModal}>Hủy</button>
                  <button className="primary-button" type="button" disabled={isSaving}
                    onClick={() => void saveStatusFromModal(room)}>
                    {isSaving ? 'Đang lưu...' : 'Lưu thay đổi'}
                  </button>
                </footer>
              </section>
            </div>
          )
        })()}
        {/* =========================
            LỊCH SỬ
           ========================= */}
{selectedHistoryRoomId !== null && (
  <div
    className="room-history-backdrop"
    onMouseDown={(event) => {
      if (event.target === event.currentTarget) {
        setSelectedHistoryRoomId(null)
      }
    }}
  >
    <section
      className="room-history-modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="room-history-title"
    >
      {/* HEADER */}
      <div className="room-history-modal-header">
        <div>
          <span className="section-kicker">
            LỊCH SỬ HOẠT ĐỘNG
          </span>
          <h2 id="room-history-title">
            Phòng{' '}
            {rooms.find(
              (room) => room.id === selectedHistoryRoomId,
            )?.roomNumber ?? selectedHistoryRoomId}
          </h2>
        </div>
        <button
          type="button"
          className="room-history-modal-close"
          aria-label="Đóng lịch sử"
          onClick={() => setSelectedHistoryRoomId(null)}
        >
          ✕
        </button>
      </div>
      {/* BODY */}
      <div className="room-history-modal-body">
        {isHistoryLoading ? (
          <p className="room-history-modal-message">
            Đang tải lịch sử...
          </p>
        ) : historyError ? (
          <div className="room-history-modal-message">
            <p role="alert">{historyError}</p>
            <button
              type="button"
              className="secondary-button"
              onClick={() => {
                void loadRoomHistory(selectedHistoryRoomId)
              }}
            >
              Thử lại
            </button>
          </div>
        ) : (
          (historyByRoom[selectedHistoryRoomId] ?? []).length === 0 ? (
            <p className="room-history-modal-message">
              Chưa có lịch sử thay đổi trạng thái cho phòng này.
            </p>
          ) : (
            <ol className="history-list">
              {(historyByRoom[selectedHistoryRoomId] ?? []).map(
                (item) => (
                  <li className="history-item" key={item.id}>
                    <div className="history-item-top">
                      <strong>
                        {item.previousStatus
                          ? (ROOM_STATUS_LABELS[item.previousStatus] ??
                              item.previousStatus)
                          : 'Chưa có trạng thái'}
                        {' → '}
                        {ROOM_STATUS_LABELS[item.newStatus] ??
                          item.newStatus}
                      </strong>
                      <span className="history-item-time">
                        {new Date(item.changedAt).toLocaleString('vi-VN')}
                      </span>
                    </div>
                    <span>
                      Người thao tác: {item.changedBy || 'Không xác định'}
                    </span>
                    {item.maintenanceReason && (
                      <span className="history-item-maintenance">
                        Lý do bảo trì: {item.maintenanceReason}
                        <br />
                        Từ: {item.maintenanceStartDate || 'Chưa xác định'}
                        {' → '}
                        Đến: {item.maintenanceEndDate || 'Chưa xác định'}
                      </span>
                    )}
                  </li>
                ),
              )}
            </ol>
          )
        )}
      </div>
      {/* FOOTER */}
      <div className="room-history-modal-footer">
        <button
          type="button"
          className="secondary-button"
          onClick={() => setSelectedHistoryRoomId(null)}
        >
          Đóng
        </button>
      </div>
    </section>
  </div>
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
                    <label htmlFor="incident-severity-select">
                      Mức độ sự cố <span style={{ color: '#dc2626' }}>*</span>
                    </label>
                    <select
                      id="incident-severity-select"
                      className="incident-severity-select"
                      value={incidentModal.severity}
                      disabled={incidentModal.submitting}
                      onChange={(e) => setIncidentModal((prev) => prev && ({
                        ...prev,
                        severity: e.target.value as IncidentSeverity | '',
                        error: null,
                        retryRequest: null,
                      }))}
                      required
                    >
                      <option value="" disabled>Chọn mức độ sự cố</option>
                      <option value="NHE">Nhẹ</option>
                      <option value="NGHIEM_TRONG">Nghiêm trọng</option>
                    </select>
                  </div>
                  <div className="form-group">
                    <label htmlFor="incident-note-input">
                      Mô tả sự cố <span style={{ color: '#dc2626' }}>*</span>
                    </label>
                    <textarea
                      id="incident-note-input"
                      className="incident-textarea"
                      rows={4}
                      placeholder="Nhập chi tiết sự cố tại phòng (VD: Hỏng khóa cửa, hỏng điều hòa, sự cố đường ống nước...)"
                      value={incidentModal.note}
                      maxLength={2001}
                      onChange={(e) =>
                        setIncidentModal((prev) =>
                          prev ? { ...prev, note: e.target.value, error: null, retryRequest: null } : null,
                        )
                      }
                      disabled={incidentModal.submitting}
                      required
                      autoFocus
                      aria-describedby="incident-description-count"
                    />
                    <p id="incident-description-count" className="incident-hint">
                      {incidentModal.note.trim().length}/2000 ký tự (không tính khoảng trắng đầu/cuối)
                    </p>
                    <p className="incident-hint">
                      Phòng sẽ được chuyển sang trạng thái Bảo trì và ghi chú sự cố sẽ hiển thị cho Lễ tân.
                    </p>
                  </div>
                  {incidentModal.error && <p className="incident-error" role="alert">{incidentModal.error}</p>}
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
                    disabled={incidentModal.submitting}
                  >
                    {incidentModal.submitting ? 'Đang gửi...' : 'Gửi báo cáo sự cố'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}
        {checkoutDialog && (
          <div className="checkout-modal-backdrop" role="presentation">
            <section className="checkout-modal-card" role="dialog" aria-modal="true" aria-labelledby="checkout-modal-title">
              <h2 id="checkout-modal-title">Quyết toán và trả phòng</h2>
              <p>{checkoutDialog.preview.guestName} · Booking {checkoutDialog.preview.bookingCode} · Phòng {checkoutDialog.preview.roomNumber}</p>
              {checkoutDialog.preview.warning && <p className="checkout-warning">{checkoutDialog.preview.warning}</p>}
              {!checkoutDialog.preview.checkoutAllowed && <p className="checkout-error" role="alert">{checkoutDialog.preview.checkoutBlockedReason}</p>}
              {checkoutDialog.preview.earlyCheckout && (
                <section className="checkout-warning" aria-labelledby="early-checkout-title">
                  <h3 id="early-checkout-title">Trả phòng sớm</h3>
                  <dl className="checkout-totals">
                    <div><dt>Số đêm đặt</dt><dd>{earlyCheckoutSummary?.bookedNights}</dd></div>
                    <div><dt>Số đêm đã sử dụng</dt><dd>{earlyCheckoutSummary?.usedNights}</dd></div>
                    <div><dt>Số đêm chưa sử dụng</dt><dd>{earlyCheckoutSummary?.unusedNights}</dd></div>
                    <div><dt>Giá trị phòng chưa sử dụng (tham khảo)</dt><dd>{earlyCheckoutSummary?.unusedRoomValue === null ? 'Không có dữ liệu' : `${checkoutMoney.format(earlyCheckoutSummary?.unusedRoomValue ?? 0)} đ`}</dd></div>
                  </dl>
                  {earlyCheckoutSummary?.unusedRoomValueWarning && <p>{earlyCheckoutSummary.unusedRoomValueWarning}</p>}
                  <p>{earlyCheckoutSummary?.warning}</p>
                  <label className="checkout-field">Lý do trả phòng sớm
                    <textarea value={checkoutDialog.earlyCheckoutReason} maxLength={500} rows={3} aria-invalid={checkoutDialog.earlyCheckoutReasonTouched && Boolean(getEarlyCheckoutReasonError(true, checkoutDialog.earlyCheckoutReason))} aria-describedby="early-checkout-reason-error" onChange={(event) => setCheckoutDialog((current) => current ? { ...current, earlyCheckoutReason: event.target.value, earlyCheckoutReasonTouched: true, error: null } : current)} />
                  </label>
                  {checkoutDialog.earlyCheckoutReasonTouched && getEarlyCheckoutReasonError(true, checkoutDialog.earlyCheckoutReason) && <p id="early-checkout-reason-error" className="checkout-error" role="alert">{getEarlyCheckoutReasonError(true, checkoutDialog.earlyCheckoutReason)}</p>}
                  <small>{checkoutDialog.earlyCheckoutReason.length}/500 ký tự</small>
                </section>
              )}
              <dl className="checkout-totals">
                <div><dt>Giờ checkout chuẩn</dt><dd>{checkoutDialog.preview.checkoutTimeSnapshot.slice(0, 5)}</dd></div>
                <div><dt>Thời điểm server</dt><dd>{new Date(checkoutDialog.preview.serverCheckoutTime).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })}</dd></div>
                {!checkoutDialog.preview.earlyCheckout && <>
                  <div><dt>Số giờ tính phí</dt><dd>{checkoutDialog.preview.lateCheckoutHours}</dd></div>
                  <div><dt>Đơn giá theo giờ</dt><dd>{checkoutDialog.preview.lateCheckoutFeePerHour === null ? '—' : `${checkoutMoney.format(checkoutDialog.preview.lateCheckoutFeePerHour)} đ`}</dd></div>
                  <div><dt>Phụ thu trả muộn ({checkoutDialog.preview.lateCheckoutPricingBasis})</dt><dd>{checkoutMoney.format(checkoutDialog.preview.lateCheckoutFee)} đ</dd></div>
                </>}
              </dl>
              {checkoutDialog.preview.nightlyBreakdownReliable && (
                <div className="checkout-night-table-wrap">
                  <table className="checkout-night-table">
                    <thead><tr><th>Đêm</th><th>Giá phòng</th><th>Phụ thu đã chốt</th><th>Tổng</th></tr></thead>
                    <tbody>{checkoutDialog.preview.nightlyPrices.map((night) => (
                      <tr key={night.date}><td>{night.date} · {night.label}</td><td>{checkoutMoney.format(night.roomPrice)} đ</td><td>{checkoutMoney.format(night.surchargeAmount)} đ</td><td>{checkoutMoney.format(night.total)} đ</td></tr>
                    ))}</tbody>
                  </table>
                </div>
              )}
              <dl className="checkout-totals">
                <div><dt>Tổng tiền đặt phòng đã chốt (đã bao gồm phụ thu)</dt><dd>{checkoutMoney.format(checkoutDialog.preview.roomTotal)} đ</dd></div>
                {checkoutDialog.preview.surchargeBreakdownComplete && checkoutDialog.preview.roomTotalExcludingSurcharge !== null && (
                  <div><dt>Tiền phòng chưa gồm phụ thu</dt><dd>{checkoutMoney.format(checkoutDialog.preview.roomTotalExcludingSurcharge)} đ</dd></div>
                )}
                <div><dt>Phụ thu thêm người đã chốt{checkoutDialog.preview.surchargeBreakdownComplete ? ' (đã gồm trong tổng booking)' : ''}</dt><dd>{checkoutMoney.format(checkoutDialog.preview.surchargeAmount)} đ</dd></div>
                {checkoutDialog.preview.surchargeBreakdownComplete && checkoutDialog.preview.surchargeAmount > 0 && (
                  <div><dt>Chi tiết phụ thu thêm người</dt><dd>{checkoutDialog.preview.extraGuestCount} khách × {checkoutMoney.format(checkoutDialog.preview.extraPersonFeeSnapshot ?? 0)} đ/người/đêm × {checkoutDialog.preview.surchargeNights} đêm = {checkoutMoney.format(checkoutDialog.preview.surchargeAmount)} đ</dd></div>
                )}
                {checkoutDialog.preview.surchargeBreakdownWarning && <div><dt>Chi tiết phụ thu</dt><dd>{checkoutDialog.preview.surchargeBreakdownWarning}</dd></div>}
                {checkoutDialog.preview.bookingSurcharges.map((line) => (
                  <div key={line.id}>
                    <dt>Phụ thu phát sinh: {line.type === 'THEM_NGUOI' ? 'thêm người' : 'thêm giường'}{line.note ? ` · ${line.note}` : ''}{line.priceOverridden ? ` · giá đã sửa (mặc định ${checkoutMoney.format(line.defaultUnitPrice)} đ): ${line.priceOverrideReason}` : ''}</dt>
                    <dd>{line.quantity} × {checkoutMoney.format(line.unitPrice)} đ × {line.nights} đêm = {checkoutMoney.format(line.amount)} đ</dd>
                  </div>
                ))}
                {checkoutDialog.preview.bookingSurcharges.length > 0 && (
                  <div><dt>Tổng phụ thu phát sinh</dt><dd>{checkoutMoney.format(checkoutDialog.preview.bookingSurchargeTotal)} đ</dd></div>
                )}
                <div><dt><strong>Tổng phải thanh toán (trước khi trừ cọc)</strong></dt><dd><strong>{checkoutMoney.format(checkoutDialog.preview.settlementTotalBeforeDeposit)} đ</strong></dd></div>
                <div><dt>Tổng cọc hiện tại</dt><dd>{checkoutMoney.format(checkoutDialog.preview.depositTotal)} đ</dd></div>
                <div><dt>{checkoutDialog.preview.settlementType === 'COLLECT' ? 'Cần thu thêm' : checkoutDialog.preview.settlementType === 'REFUND' ? 'Cần hoàn lại' : 'Đã thanh toán đủ'}</dt><dd>{checkoutMoney.format(checkoutDialog.preview.amount)} đ</dd></div>
              </dl>
              {checkoutDialog.preview.settlementType !== 'ZERO' && (
                <>
                  <label className="checkout-field">{checkoutDialog.preview.settlementType === 'COLLECT' ? 'Phương thức thu' : 'Phương thức hoàn'}
                    <select value={checkoutDialog.paymentMethod} onChange={(event) => setCheckoutDialog((current) => current ? { ...current, paymentMethod: event.target.value as 'CASH' | 'BANK_TRANSFER' } : current)}>
                      <option value="CASH">Tiền mặt</option><option value="BANK_TRANSFER">Chuyển khoản</option>
                    </select>
                  </label>
                  {checkoutDialog.paymentMethod === 'BANK_TRANSFER' && (
                    <label className="checkout-field">Mã giao dịch
                      <input value={checkoutDialog.paymentReference} onChange={(event) => setCheckoutDialog((current) => current ? { ...current, paymentReference: event.target.value } : current)} maxLength={255} />
                    </label>
                  )}
                  <label className="checkout-confirm"><input type="checkbox" checked={checkoutDialog.confirmed} onChange={(event) => setCheckoutDialog((current) => current ? { ...current, confirmed: event.target.checked } : current)} />
                    {checkoutDialog.preview.settlementType === 'COLLECT' ? 'Tôi xác nhận đã thu đủ khoản chênh lệch.' : 'Tôi xác nhận đã hoàn khoản tiền dư thực tế.'}
                  </label>
                </>
              )}
              {checkoutDialog.error && <p className="checkout-error" role="alert">{checkoutDialog.error}</p>}
              <div className="checkout-actions">
                {(checkoutDialog.error || !checkoutDialog.preview.checkoutAllowed) && !isDemoMode && <button type="button" className="secondary-button" onClick={() => void reloadCheckoutPreview()} disabled={isSaving}>Tải lại bảng quyết toán</button>}
                <button type="button" className="secondary-button" onClick={() => setCheckoutDialog(null)} disabled={isSaving}>Đóng</button>
                <button type="button" className="primary-button" onClick={() => void confirmCheckout()} disabled={isSaving || !checkoutDialog.preview.checkoutAllowed || (checkoutDialog.preview.earlyCheckout && !checkoutDialog.earlyCheckoutReason.trim()) || (checkoutDialog.preview.settlementType !== 'ZERO' && (!checkoutDialog.confirmed || (checkoutDialog.paymentMethod === 'BANK_TRANSFER' && !checkoutDialog.paymentReference.trim())))}>
                  {isSaving ? 'Đang xử lý…' : 'Xác nhận quyết toán và trả phòng'}
                </button>
              </div>
            </section>
          </div>
        )}
        {earlyCheckInWarningOpen && (
          <div
            className="early-checkin-modal-backdrop"
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="early-checkin-modal-title"
            aria-describedby="early-checkin-modal-description"
          >
            <section className="early-checkin-modal-card">
              <h3 id="early-checkin-modal-title">Cảnh báo nhận phòng sớm</h3>
              <p id="early-checkin-modal-description">
                Hiện tại chưa đến 14:00 theo múi giờ Asia/Ho_Chi_Minh. Bạn có muốn
                tiếp tục nhận phòng không? Theo cấu hình hiện tại, nhận phòng sớm
                không phát sinh phụ thu.
              </p>
              <div className="early-checkin-modal-actions">
                <button
                  type="button"
                  className="secondary-button"
                  onClick={() => setEarlyCheckInWarningOpen(false)}
                  disabled={isSaving}
                >
                  Hủy thao tác
                </button>
                <button
                  type="button"
                  className="primary-button"
                  onClick={() => {
                    setEarlyCheckInWarningOpen(false)
                    void submitCheckIn(true)
                  }}
                  disabled={isSaving}
                >
                  Tiếp tục
                </button>
              </div>
            </section>
          </div>
        )}
      </main>
    </div>
  )
}
