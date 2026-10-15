import { canChangeBookingRoom, roomChangeOptions } from './bookingRoomChangeActions'
import { bookingActionsFor, isNoShowEligible } from './bookingActionVisibility'
import './BookingListPage.css'
import { ApiRequestError } from '../services/apiClient'
import type {
  BookingStatus,
  BookingListItem,
  BookingChangePreview,
  AssignableRoom,
  BookingRoomChangeHistory,
} from '../types/booking'
import { useEffect, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent as ReactKeyboardEvent } from 'react'
import { createPortal } from 'react-dom'
import type { BookingDetailResponse } from '../services/bookingService'
import { getClosedBookingCheckoutDisplay } from '../services/checkoutAc3'
import { createBookingDepositAdjustment } from '../services/bookingService'
import { hasPermission } from '../permissions/rolePermissions'
import {
  confirmBooking,
  getBookingDetails,
  searchBookings,
  updateBooking,
  previewBookingChange,
  getAvailableRooms,
  assignBookingRoom,
  changeBookingRoom,
  getBookingRoomChangeHistory,
  markBookingNoShow,
} from '../services/bookingService'
import { getRoomTypes } from '../services/roomTypeService'
import type { RoomType } from '../types/roomType'
import { BookingCancellationInfoSection } from '../components/BookingCancellationInfoSection'
import { BookingCheckedInCancelNotice } from '../components/BookingCheckedInCancelNotice'
import { confirmRoomSelection as runRoomAssignment, reloadAssignableRooms as runRoomReload } from './bookingRoomActions.ts'
import { submitRoomChange as runRoomChange } from './bookingRoomChangeActions.ts'
import CancelBookingDialog from '../components/CancelBookingDialog'
import { BookingSurchargeSection } from '../components/BookingSurchargeSection'

const STATUS_LABELS: Record<string, string> = {
  CHO_XAC_NHAN: 'Chờ xác nhận',
  DA_XAC_NHAN: 'Đã xác nhận',
  DA_HUY: 'Đã huỷ',
  DA_NHAN_PHONG: 'Đã nhận phòng',
  DA_TRA_PHONG: 'Đã trả phòng',
  DA_HET_HAN: 'Đã hết hạn',
  NO_SHOW: 'Không đến',
}

const STATUS_CLASS_NAMES: Record<string, string> = {
  CHO_XAC_NHAN: 'pending',
  DA_XAC_NHAN: 'confirmed',
  DA_HUY: 'cancelled',
  DA_NHAN_PHONG: 'checked-in',
  DA_TRA_PHONG: 'checked-out',
  DA_HET_HAN: 'cancelled',
  NO_SHOW: 'cancelled',
}

const EMPTY_FILTERS = {
  keyword: '',
  status: '' as BookingStatus | '',
  checkInFrom: '',
  checkInTo: '',
}

function formatDate(value: string) {
  if (!value) return ''
  const parts = value.split('-')
  if (parts.length === 3) {
    const [year, month, day] = parts
    return `${day}/${month}/${year}`
  }
  return value
}

function formatCurrency(value: number) {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
    maximumFractionDigits: 0,
  }).format(value)
}

export function BookingListPage({ role }: { role: string }) {
  const canAdjustDeposit = hasPermission(role, 'bookings:deposit-adjust')
  const canCancel = hasPermission(role, 'bookings:cancel')
  const canManage = hasPermission(role, 'bookings:manage')
  const canNoShow = canManage || canCancel || role === 'RECEPTIONIST'
  const canAddSurcharge = hasPermission(role, 'bookings:surcharge-add')
  const [bookings, setBookings] = useState<BookingListItem[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirmingBooking, setConfirmingBooking] = useState<BookingListItem | null>(null)
  const [confirmSaving, setConfirmSaving] = useState(false)
  const [confirmError, setConfirmError] = useState<string | null>(null)
  const [confirmForm, setConfirmForm] = useState({
    amount: '',
    paymentMethod: 'CASH' as 'CASH' | 'BANK_TRANSFER',
    receivedDate: new Date().toLocaleDateString('en-CA'),
    paymentReference: '',
  })
  const [detailBooking, setDetailBooking] = useState<BookingListItem | null>(null)
  const [detailData, setDetailData] = useState<BookingDetailResponse | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState<string | null>(null)
  const [editDetailData, setEditDetailData] = useState<BookingDetailResponse | null>(null)
  const [editDetailLoading, setEditDetailLoading] = useState(false)
  const [editDetailError, setEditDetailError] = useState<string | null>(null)
  const [adjustmentOpen, setAdjustmentOpen] = useState(false)
  const [adjustmentSaving, setAdjustmentSaving] = useState(false)
  const [adjustmentError, setAdjustmentError] = useState<string | null>(null)
  const [adjustmentForm, setAdjustmentForm] = useState({ type: 'TANG' as 'TANG' | 'GIAM', amount: '', reason: '' })
  const [editingBooking, setEditingBooking] = useState<BookingListItem | null>(null)
  const [roomTypes, setRoomTypes] = useState<RoomType[]>([])
  const [roomTypesLoading, setRoomTypesLoading] = useState(false)
  const [editSaving, setEditSaving] = useState(false)
  const [editError, setEditError] = useState<string | null>(null)
  const [editSuccess, setEditSuccess] = useState<string | null>(null)
  const [refreshVersion, setRefreshVersion] = useState(0)
  const [openActionMenu, setOpenActionMenu] = useState<number | null>(null)
  const [actionMenuPosition, setActionMenuPosition] = useState<{ top: number; left: number; maxHeight: number } | null>(null)
  const actionMenuRef = useRef<HTMLDivElement | null>(null)
  const actionTriggerRefs = useRef(new Map<number, HTMLButtonElement>())
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [appliedFilters, setAppliedFilters] = useState(EMPTY_FILTERS)
    // S3-02 Lát 3: huỷ booking
  const [cancellingBooking, setCancellingBooking] = useState<BookingListItem | null>(null)

  // S4-04 (SCRUM-149): Khách không đến (No-show)
  const [noShowBooking, setNoShowBooking] = useState<BookingListItem | null>(null)
  const [noShowLoading, setNoShowLoading] = useState(false)
  const [noShowError, setNoShowError] = useState<string | null>(null)
  const [noShowSuccess, setNoShowSuccess] = useState<string | null>(null)

  function startNoShow(booking: BookingListItem) {
    setNoShowBooking(booking)
    setNoShowError(null)
  }

  async function handleConfirmNoShow() {
    if (!noShowBooking || noShowLoading) return
    setNoShowLoading(true)
    setNoShowError(null)
    try {
      await markBookingNoShow(noShowBooking.id)
      setNoShowSuccess(`Đã đánh dấu khách không đến cho booking ${noShowBooking.bookingCode} và giải phóng phòng thành công.`)
      const updatedId = noShowBooking.id
      setNoShowBooking(null)
      if (detailBooking && detailBooking.id === updatedId) {
        setDetailBooking({ ...detailBooking, status: 'NO_SHOW', roomNumber: null })
      }
      setRefreshVersion((v) => v + 1)
      window.dispatchEvent(new CustomEvent('homestay:booking-changed', { detail: { bookingId: updatedId } }))
    } catch (err) {
      setNoShowError(err instanceof Error ? err.message : 'Không thể đánh dấu khách không đến.')
    } finally {
      setNoShowLoading(false)
    }
  }

  const [editForm, setEditForm] = useState({
    checkInDate: '',
    checkOutDate: '',
    roomTypeId: '',
  })
  const [previewResult, setPreviewResult] = useState<{
    key: string
    preview: BookingChangePreview | null
    error: string | null
  } | null>(null)
  const [roomBooking, setRoomBooking] = useState<BookingListItem | null>(null)
  const [assignableRooms, setAssignableRooms] = useState<AssignableRoom[]>([])
  const [selectedRoomId, setSelectedRoomId] = useState<number | null>(null)
  const [roomLoading, setRoomLoading] = useState(false)
  const [roomSaving, setRoomSaving] = useState(false)
  const [roomError, setRoomError] = useState<string | null>(null)
  const [changeRoomBooking, setChangeRoomBooking] = useState<BookingListItem | null>(null)
  const [changeRoomReason, setChangeRoomReason] = useState('')
  const [changeRoomSaving, setChangeRoomSaving] = useState(false)
  const [roomChangeHistory, setRoomChangeHistory] = useState<BookingRoomChangeHistory[]>([])

  useEffect(() => {
    if (openActionMenu === null) return
    const closeOnOutsidePointer = (event: PointerEvent) => {
      if (actionMenuRef.current?.contains(event.target as Node)) return
      if (actionTriggerRefs.current.get(openActionMenu)?.contains(event.target as Node)) return
      setOpenActionMenu(null)
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return
      event.preventDefault()
      actionTriggerRefs.current.get(openActionMenu)?.focus()
      setOpenActionMenu(null)
    }
    document.addEventListener('pointerdown', closeOnOutsidePointer)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeOnOutsidePointer)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [openActionMenu])

  useLayoutEffect(() => {
    if (openActionMenu === null) return
    const reposition = () => {
      const trigger = actionTriggerRefs.current.get(openActionMenu)
      const menu = actionMenuRef.current
      if (!trigger || !menu) return
      const rect = trigger.getBoundingClientRect()
      if (rect.bottom < 0 || rect.top > window.innerHeight || rect.right < 0 || rect.left > window.innerWidth) {
        setOpenActionMenu(null)
        return
      }
      const menuHeight = menu.getBoundingClientRect().height
      const menuWidth = menu.getBoundingClientRect().width
      const edge = 8
      const gap = 6
      const below = window.innerHeight - rect.bottom - gap - edge
      const above = rect.top - gap - edge
      const placeBelow = menuHeight <= below || (menuHeight > above && below >= above)
      const availableHeight = Math.max(0, placeBelow ? below : above)
      const top = placeBelow
        ? Math.min(rect.bottom + gap, window.innerHeight - menuHeight - edge)
        : Math.max(edge, rect.top - gap - Math.min(menuHeight, availableHeight))
      const left = Math.max(edge, Math.min(rect.right - menuWidth, window.innerWidth - menuWidth - edge))
      setActionMenuPosition({ top, left, maxHeight: Math.max(0, Math.min(menuHeight, availableHeight)) })
    }
    reposition()
    window.addEventListener('resize', reposition)
    window.addEventListener('scroll', reposition, true)
    return () => {
      window.removeEventListener('resize', reposition)
      window.removeEventListener('scroll', reposition, true)
    }
  }, [openActionMenu])

  function toggleActionMenu(bookingId: number) {
    if (openActionMenu === bookingId) {
      setOpenActionMenu(null)
      return
    }
    setActionMenuPosition(null)
    setOpenActionMenu(bookingId)
  }

  function closeActionMenuAndRun(action: () => void) {
    setOpenActionMenu(null)
    action()
  }

  function handleActionMenuKeyDown(event: ReactKeyboardEvent<HTMLDivElement>) {
    const items = Array.from(event.currentTarget.querySelectorAll<HTMLButtonElement>('[role="menuitem"]'))
    if (items.length === 0) return
    const currentIndex = items.indexOf(document.activeElement as HTMLButtonElement)
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp' || event.key === 'Home' || event.key === 'End') {
      event.preventDefault()
      const nextIndex = event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1
        : (currentIndex + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length
      items[nextIndex].focus()
    }
  }

  async function openRoomChange(booking: BookingListItem) {
    setChangeRoomBooking(booking)
    setChangeRoomReason('')
    setRoomChangeHistory([])
    setAssignableRooms([])
    setSelectedRoomId(null)
    setRoomError(null)
    setRoomLoading(true)
    try {
      const [rooms, history] = await Promise.all([getAvailableRooms(booking.id), getBookingRoomChangeHistory(booking.id)])
      setAssignableRooms(roomChangeOptions(booking, rooms))
      setRoomChangeHistory(history)
    } catch (err) {
      setRoomError(err instanceof Error ? err.message : 'Không tải được dữ liệu đổi phòng.')
    } finally { setRoomLoading(false) }
  }

  async function submitRoomChange() {
    await runRoomChange(changeRoomBooking, selectedRoomId, changeRoomReason, {
      changeBookingRoom, reloadBookingData, getBookingRoomChangeHistory,
      setRoomChangeHistory, setRoomError, setRoomSaving: setChangeRoomSaving,
      setChangeRoomBooking, setEditSuccess,
    })
  }

  async function reloadAssignableRooms() {
    await runRoomReload(roomBooking, selectedRoomId, {
      getAvailableRooms, assignBookingRoom, setAssignableRooms, setSelectedRoomId, setRoomError, setRoomLoading,
      setRoomSaving, setRoomBooking, setEditSuccess, reloadBookings: reloadBookingData,
    })
  }

  async function startRoomSelection(booking: BookingListItem) {
    if (booking.status !== 'DA_XAC_NHAN' || booking.roomConfirmedAt) return
    setRoomBooking(booking)
    setAssignableRooms([])
    setSelectedRoomId(null)
    setRoomError(null)
    setRoomLoading(true)
    try {
      const rooms = await getAvailableRooms(booking.id)
      const latestBooking = bookings.find((item) => item.id === booking.id)
      if (!latestBooking || latestBooking.status !== 'DA_XAC_NHAN' || latestBooking.roomConfirmedAt) {
        setRoomError('Booking không còn ở trạng thái có thể chốt phòng. Hãy tải lại danh sách booking.')
        setRoomBooking(null)
        return
      }
      setAssignableRooms(rooms)
      const currentRoom = rooms.find((room) => room.roomNumber === booking.roomNumber)
      if (currentRoom) setSelectedRoomId(currentRoom.id)
      else if (rooms.length === 1) setSelectedRoomId(rooms[0].id)
      if (rooms.length === 0) setRoomError('Không còn phòng phù hợp trong toàn bộ thời gian lưu trú.')
    } catch (err) {
      setRoomError(err instanceof Error ? err.message : 'Không tải được danh sách phòng.')
    } finally {
      setRoomLoading(false)
    }
  }

  async function confirmRoomSelection() {
    await runRoomAssignment(roomBooking, selectedRoomId, assignableRooms, {
      getAvailableRooms, assignBookingRoom, setAssignableRooms, setSelectedRoomId, setRoomError, setRoomLoading,
      setRoomSaving, setRoomBooking, setEditSuccess, reloadBookings: reloadBookingData,
    })
  }

  async function reloadBookingData() {
    setOpenActionMenu(null)
    const result = await searchBookings({
      keyword: appliedFilters.keyword.trim() || undefined,
      status: appliedFilters.status || undefined,
      checkInFrom: appliedFilters.checkInFrom || undefined,
      checkInTo: appliedFilters.checkInTo || undefined,
      page,
      size: 20,
    })
    setBookings(result.content || [])
    setPage(result.page ?? 0)
    setTotalPages(result.totalPages ?? 0)
    setTotalElements(result.totalElements ?? 0)
  }

  useEffect(() => {
    let cancelled = false

    async function fetchBookings() {
      setLoading(true)
      setError(null)

      try {
        const result = await searchBookings({
          keyword: appliedFilters.keyword.trim() || undefined,
          status: appliedFilters.status || undefined,
          checkInFrom: appliedFilters.checkInFrom || undefined,
          checkInTo: appliedFilters.checkInTo || undefined,
          page,
          size: 20,
        })

        if (!cancelled) {
          setBookings(result.content || [])
          setPage(result.page ?? 0)
          setTotalPages(result.totalPages ?? 0)
          setTotalElements(result.totalElements ?? 0)
        }
      } catch (err) {
        if (!cancelled) {
          setBookings([])
          setError(
            err instanceof Error
              ? err.message
              : 'Không thể tải danh sách booking.',
          )
        }
      } finally {
        if (!cancelled) {
          setLoading(false)
        }
      }
    }

    void fetchBookings()

    return () => {
      cancelled = true
    }
  }, [page, appliedFilters, refreshVersion])

  const editingBookingId = editingBooking?.id
  const hasPreviewInputs = editingBookingId !== undefined &&
    Boolean(editForm.checkInDate && editForm.checkOutDate && editForm.roomTypeId)
  const hasInvalidPreviewDates = hasPreviewInputs &&
    editForm.checkInDate >= editForm.checkOutDate
  const previewRequestKey = hasPreviewInputs && !hasInvalidPreviewDates
    ? `${editingBookingId}|${editForm.checkInDate}|${editForm.checkOutDate}|${editForm.roomTypeId}`
    : null
  const matchingPreviewResult = previewRequestKey !== null && previewResult?.key === previewRequestKey
    ? previewResult
    : null
  const preview = matchingPreviewResult?.preview ?? null
  const previewError = hasInvalidPreviewDates
    ? 'Ngày trả phòng phải sau ngày nhận phòng.'
    : matchingPreviewResult?.error ?? null
  const previewLoading = previewRequestKey !== null && matchingPreviewResult === null

  useEffect(() => {
    if (previewRequestKey === null || editingBookingId === undefined) return
    let cancelled = false
    const timer = setTimeout(() => {
      previewBookingChange(editingBookingId, {
        checkInDate: editForm.checkInDate,
        checkOutDate: editForm.checkOutDate,
        roomTypeId: Number(editForm.roomTypeId),
      })
        .then((result) => {
          if (!cancelled) setPreviewResult({ key: previewRequestKey, preview: result, error: null })
        })
        .catch((err: unknown) => {
          if (!cancelled) {
            setPreviewResult({
              key: previewRequestKey,
              preview: null,
              error: err instanceof Error
                ? err.message
                : 'Không thể tính toán giá phòng và kiểm tra phòng trống.',
            })
          }
        })
    }, 250)

    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [
    editingBookingId,
    editForm.checkInDate,
    editForm.checkOutDate,
    editForm.roomTypeId,
    previewRequestKey,
  ])

  function handleFilterSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setOpenActionMenu(null)

    if (
      filters.checkInFrom &&
      filters.checkInTo &&
      filters.checkInTo < filters.checkInFrom
    ) {
      setError('Ngày nhận phòng đến phải sau hoặc bằng ngày nhận phòng từ')
      return
    }

    setError(null)
    setPage(0)
    setAppliedFilters({ ...filters })
  }

  function handleResetFilters() {
    setOpenActionMenu(null)
    setFilters(EMPTY_FILTERS)
    setAppliedFilters(EMPTY_FILTERS)
    setPage(0)
    setError(null)
  }

  function startConfirming(booking: BookingListItem) {
    if (booking.status !== 'CHO_XAC_NHAN' || booking.holdExpired) return
    setConfirmingBooking(booking)
    setConfirmError(null)
    setConfirmForm({
      amount: '',
      paymentMethod: 'CASH',
      receivedDate: new Date().toLocaleDateString('en-CA'),
      paymentReference: '',
    })
  }
     function startCancelling(booking: BookingListItem) {
    setCancellingBooking(booking)
  }
function handleCancelled(result: { bookingCode: string; refundAmount: number }) {
    setEditSuccess(
      `Đã huỷ booking ${result.bookingCode}. Hoàn cọc: ${result.refundAmount.toLocaleString('vi-VN')} đ. Phòng đã được trả lại để bán.`,
    )
    setCancellingBooking(null)
    setRefreshVersion((version) => version + 1)
  }

  async function handleConfirmBooking(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!confirmingBooking || confirmSaving) return
    if (confirmingBooking.status !== 'CHO_XAC_NHAN' || confirmingBooking.holdExpired) return
    const amount = Number(confirmForm.amount)
    if (!confirmForm.amount || !Number.isSafeInteger(amount) || amount <= 0) {
      setConfirmError('Số tiền cọc phải là số nguyên dương.')
      return
    }
    if (amount > confirmingBooking.totalAmount) {
      setConfirmError('Tiền cọc không được lớn hơn tổng tiền đặt phòng.')
      return
    }
    if (!confirmForm.receivedDate) {
      setConfirmError('Vui lòng chọn ngày nhận tiền.')
      return
    }
    if (confirmForm.paymentMethod === 'BANK_TRANSFER' && !confirmForm.paymentReference.trim()) {
      setConfirmError('Chuyển khoản phải có mã giao dịch.')
      return
    }
    setConfirmSaving(true)
    setConfirmError(null)
    try {
      await confirmBooking(confirmingBooking.id, {
        amount,
        paymentMethod: confirmForm.paymentMethod,
        receivedDate: confirmForm.receivedDate,
        paymentReference: confirmForm.paymentReference.trim() || null,
      })
      setEditSuccess(`Đã xác nhận booking ${confirmingBooking.bookingCode} và ghi nhận tiền cọc.`)
      setConfirmingBooking(null)
      setRefreshVersion((version) => version + 1)
    } catch (err) {
      setConfirmError(err instanceof Error ? err.message : 'Không thể xác nhận booking.')
      if (err instanceof ApiRequestError && err.status === 409) {
        setConfirmingBooking(null)
        setEditSuccess(null)
        setRefreshVersion((version) => version + 1)
      }
    } finally {
      setConfirmSaving(false)
    }
  }

  async function openDetails(booking: BookingListItem) {
    setDetailBooking(booking)
    setDetailData(null)
    setDetailError(null)
    setDetailLoading(true)
    try {
      const [data, roomHistory] = await Promise.all([
        getBookingDetails(booking.id), getBookingRoomChangeHistory(booking.id),
      ])
      setDetailData(data)
      setRoomChangeHistory(roomHistory)
    } catch (err) {
      setDetailError(err instanceof Error ? err.message : 'Không tải được chi tiết booking.')
    } finally {
      setDetailLoading(false)
    }
  }

  function updateEditForm(update: (current: typeof editForm) => typeof editForm) {
    setEditForm(update)
    setPreviewResult(null)
  }

  function startEditing(booking: BookingListItem) {
    setEditingBooking(booking)
    setEditForm({
      checkInDate: booking.checkInDate,
      checkOutDate: booking.checkOutDate,
      roomTypeId: booking.roomTypeId === null ? '' : String(booking.roomTypeId),
    })
    setPreviewResult(null)
    setEditError(null)
    setEditSuccess(null)
    setEditDetailData(null)
    setEditDetailError(null)
    setEditDetailLoading(true)
    getBookingDetails(booking.id)
      .then(setEditDetailData)
      .catch((err: unknown) => setEditDetailError(err instanceof Error ? err.message : 'Không tải được thông tin tiền cọc.'))
      .finally(() => setEditDetailLoading(false))
    setRoomTypesLoading(true)
    getRoomTypes()
      .then(setRoomTypes)
      .catch((err: unknown) => {
        setEditError(
          err instanceof Error
            ? err.message
            : 'Không thể tải danh sách loại phòng.',
        )
      })
      .finally(() => setRoomTypesLoading(false))
  }

  async function handleCreateDepositAdjustment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editingBooking || !editDetailData?.deposit || adjustmentSaving) return
    const amount = Number(adjustmentForm.amount)
    const currentTotal = Number(editDetailData.currentDepositTotal ?? editDetailData.deposit.amount)
    const projectedTotal = currentTotal + (adjustmentForm.type === 'TANG' ? amount : -amount)
    if (!Number.isSafeInteger(amount) || amount <= 0 || !adjustmentForm.reason.trim() || adjustmentForm.reason.length > 500) return
    if (projectedTotal < 0 || projectedTotal > editingBooking.totalAmount) {
      setAdjustmentError('Tổng tiền cọc sau điều chỉnh phải từ 0 đến tổng tiền booking.')
      return
    }
    setAdjustmentSaving(true)
    setAdjustmentError(null)
    try {
      await createBookingDepositAdjustment(editingBooking.id, {
        type: adjustmentForm.type,
        amount,
        reason: adjustmentForm.reason.trim(),
      })
      const refreshed = await getBookingDetails(editingBooking.id)
      setEditDetailData(refreshed)
      setAdjustmentOpen(false)
      setAdjustmentForm({ type: 'TANG', amount: '', reason: '' })
      setEditSuccess(`Đã ghi nhận điều chỉnh tiền cọc cho booking ${editingBooking.bookingCode}.`)
    } catch (err) {
      setAdjustmentError(err instanceof Error ? err.message : 'Không thể lưu điều chỉnh tiền cọc.')
    } finally {
      setAdjustmentSaving(false)
    }
  }

  async function handleUpdateBooking(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editingBooking) return

    if (!editForm.checkInDate || !editForm.checkOutDate || !editForm.roomTypeId) {
      setEditError('Vui lòng nhập đầy đủ ngày nhận, ngày trả và loại phòng.')
      return
    }
    if (editForm.checkInDate >= editForm.checkOutDate) {
      setEditError('Ngày trả phòng phải sau ngày nhận phòng.')
      return
    }
    if (preview && !preview.available) {
      setEditError('Loại phòng đã hết trong khoảng ngày đã chọn. Vui lòng chọn ngày hoặc loại phòng khác.')
      return
    }

    setEditSaving(true)
    setEditError(null)
    try {
      const updated = await updateBooking(editingBooking.id, {
        checkInDate: editForm.checkInDate,
        checkOutDate: editForm.checkOutDate,
        roomTypeId: Number(editForm.roomTypeId),
      })
      setEditSuccess(
        `Đã cập nhật booking ${editingBooking.bookingCode}. Tổng tiền mới: ${formatCurrency(updated.totalAmount)}.`,
      )
      setEditingBooking(null)
      setRefreshVersion((version) => version + 1)
    } catch (err) {
      setEditError(
        err instanceof Error ? err.message : 'Không thể cập nhật booking.',
      )
    } finally {
      setEditSaving(false)
    }
  }

  const closedCheckoutDisplay = detailData
    ? getClosedBookingCheckoutDisplay(detailData.status, detailData.checkedOutAt, detailData.earlyCheckoutReason)
    : null

  return (
    <section className="panel booking-page">
      <div className="panel-heading">
        <div>
          <h2>Booking mới</h2>
          <p>Danh sách được sắp xếp theo thời điểm tạo mới nhất.</p>
        </div>
      </div>

      <form className="booking-filters" onSubmit={handleFilterSubmit}>
        <label>
          Từ khóa
          <input
            type="search"
            value={filters.keyword}
            placeholder="Tên khách hoặc số điện thoại"
            onChange={(event) =>
              setFilters((current) => ({
                ...current,
                keyword: event.target.value,
              }))
            }
          />
        </label>

        <label>
          Trạng thái
          <select
            value={filters.status}
            onChange={(event) =>
              setFilters((current) => ({
                ...current,
                status: event.target.value as BookingStatus | '',
              }))
            }
          >
            <option value="">Tất cả trạng thái</option>
            <option value="CHO_XAC_NHAN">Chờ xác nhận</option>
            <option value="DA_XAC_NHAN">Đã xác nhận</option>
            <option value="DA_HUY">Đã huỷ</option>
            <option value="DA_NHAN_PHONG">Đã nhận phòng</option>
            <option value="DA_TRA_PHONG">Đã trả phòng</option>
            <option value="DA_HET_HAN">Đã hết hạn</option>
            <option value="NO_SHOW">Không đến</option>
          </select>
        </label>

        <label>
          Nhận phòng từ
          <input
            type="date"
            value={filters.checkInFrom}
            onChange={(event) =>
              setFilters((current) => ({
                ...current,
                checkInFrom: event.target.value,
              }))
            }
          />
        </label>

        <label>
          Nhận phòng đến
          <input
            type="date"
            value={filters.checkInTo}
            onChange={(event) =>
              setFilters((current) => ({
                ...current,
                checkInTo: event.target.value,
              }))
            }
          />
        </label>

        <button type="submit">Lọc</button>

        <button type="button" onClick={handleResetFilters}>
          Xoá lọc
        </button>
      </form>

      {error && <div className="alert">{error}</div>}
      {editSuccess && <div className="booking-success" role="status">{editSuccess}</div>}

      {loading ? (
        <p className="empty-state">Đang tải danh sách booking...</p>
      ) : bookings.length === 0 ? (
        <p className="empty-state">Chưa có booking nào.</p>
      ) : (
        <>
          <div className="booking-table-wrapper">
            <table className="booking-table">
              <caption className="visually-hidden">
                Danh sách booking mới
              </caption>

              <thead>
                <tr>
                  <th>Mã booking</th>
                  <th>Tên khách</th>
                  <th>Loại phòng</th>
                  <th>Phòng</th>
                  <th>Ngày nhận</th>
                  <th>Ngày trả</th>
                  <th>Tổng tiền</th>
                  <th>Trạng thái</th>
                  <th>Thao tác</th>
                </tr>
              </thead>

              <tbody>
                {bookings.map((booking) => (
                  <tr
                    key={booking.bookingCode}
                    className={booking.holdExpired ? 'row-hold-expired' : ''}
                  >
                    <td>
                      <strong>{booking.bookingCode}</strong>
                    </td>
                    <td>
                      <div>{booking.guestName}</div>
                      {booking.guestPhone && (
                        <small>{booking.guestPhone}</small>
                      )}
                    </td>
                    <td>{booking.roomTypeNameSnapshot}</td>
                    <td>
                      <div>{booking.roomNumber ?? 'Chưa gán'}</div>
                      <small>{booking.roomConfirmedAt ? 'Phòng đã chốt' : booking.roomNumber ? 'Phòng giữ tạm' : 'Chưa giữ phòng'}</small>
                      {booking.roomConfirmedAt && <small>{booking.roomConfirmedBy ? ` · ${booking.roomConfirmedBy}` : ''}{` · ${new Date(booking.roomConfirmedAt).toLocaleString('vi-VN')}`}</small>}
                    </td>
                    <td>{formatDate(booking.checkInDate)}</td>
                    <td>{formatDate(booking.checkOutDate)}</td>
                    <td>{formatCurrency(booking.totalAmount)}</td>
                    <td>
                      <span
                        className={`booking-status ${
                          STATUS_CLASS_NAMES[booking.status] ?? 'pending'
                        }`}
                      >
                        {STATUS_LABELS[booking.status] ?? booking.status}
                      </span>
                      {booking.holdExpired && (
                        <span className="badge-expired">
                          Quá hạn giữ chỗ 24 giờ
                        </span>
                      )}
                    </td>
                    <td className="booking-actions-cell">
                      <button type="button" className="booking-detail-button" onClick={() => void openDetails(booking)}>
                        <svg aria-hidden="true" viewBox="0 0 20 20"><path d="M2 10s2.8-5 8-5 8 5 8 5-2.8 5-8 5-8-5-8-5Z" /><circle cx="10" cy="10" r="2.2" /></svg>
                        Chi tiết
                      </button>
                      {(() => {
                        const actions = bookingActionsFor(
                          booking,
                          { manage: canManage, cancel: canCancel, noShow: canNoShow },
                          canChangeBookingRoom(role, booking, new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' })),
                        )
                        const hasActions = actions.length > 0
                        if (!hasActions) return null
                        const isOpen = openActionMenu === booking.id
                        return <>
                          <button
                            type="button"
                            className="booking-actions-trigger"
                            aria-haspopup="menu"
                            aria-expanded={isOpen}
                            aria-controls={isOpen ? `booking-actions-menu-${booking.id}` : undefined}
                            aria-label={`Thao tác booking ${booking.bookingCode}`}
                            ref={(element) => {
                              if (element) actionTriggerRefs.current.set(booking.id, element)
                              else actionTriggerRefs.current.delete(booking.id)
                            }}
                            onClick={() => toggleActionMenu(booking.id)}
                            onKeyDown={(event) => {
                              if (event.key === 'ArrowDown' && !isOpen) {
                                event.preventDefault()
                                setOpenActionMenu(booking.id)
                                window.setTimeout(() => actionMenuRef.current?.querySelector<HTMLButtonElement>('[role="menuitem"]')?.focus(), 0)
                              }
                            }}
                          ><svg aria-hidden="true" viewBox="0 0 20 20"><path d="M4 5h12M4 10h12M4 15h12" /></svg>Thao tác <span className="booking-actions-chevron" aria-hidden="true">▾</span></button>
                          {isOpen && createPortal(
                            <div
                              className="booking-actions-menu"
                              id={`booking-actions-menu-${booking.id}`}
                              role="menu"
                              aria-label={`Thao tác booking ${booking.bookingCode}`}
                              ref={actionMenuRef}
                              style={{
                                top: actionMenuPosition?.top ?? 0,
                                left: actionMenuPosition?.left ?? 0,
                                maxHeight: actionMenuPosition?.maxHeight,
                                visibility: actionMenuPosition ? 'visible' : 'hidden',
                              }}
                              onKeyDown={handleActionMenuKeyDown}
                            >
                              {actions.includes('confirm') && <button type="button" role="menuitem" onClick={() => closeActionMenuAndRun(() => startConfirming(booking))}><span aria-hidden="true">✓</span>Xác nhận</button>}
                              {actions.includes('assign-room') && <button type="button" role="menuitem" onClick={() => closeActionMenuAndRun(() => void startRoomSelection(booking))}><span aria-hidden="true">⌂</span>Chốt phòng</button>}
                              {actions.includes('change-room') && <button type="button" role="menuitem" onClick={() => closeActionMenuAndRun(() => void openRoomChange(booking))}><span aria-hidden="true">⇄</span>Đổi phòng</button>}
                              {actions.includes('edit') && <button type="button" role="menuitem" id={`booking-edit-btn-${booking.id}`} onClick={() => closeActionMenuAndRun(() => startEditing(booking))}><span aria-hidden="true">✎</span>Cập nhật</button>}
                              {actions.includes('cancel') && <button type="button" role="menuitem" className="booking-actions-menu-danger" onClick={() => closeActionMenuAndRun(() => startCancelling(booking))}><span aria-hidden="true">×</span>Huỷ booking</button>}
                              {actions.includes('no-show') && (() => {
                                const eligibility = isNoShowEligible(booking)
                                return (
                                  <button
                                    type="button"
                                    role="menuitem"
                                    id={`booking-no-show-btn-${booking.id}`}
                                    className={`booking-actions-menu-warning ${eligibility.eligible ? 'is-active' : ''}`}
                                    disabled={!eligibility.eligible}
                                    title={eligibility.reason || 'Đánh dấu khách không đến'}
                                    onClick={() => closeActionMenuAndRun(() => startNoShow(booking))}
                                  >
                                    <span aria-hidden="true">⏱</span>Khách không đến
                                  </button>
                                )
                              })()}
                            </div>, document.body,
                          )}
                        </>
                      })()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="booking-pagination">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => { setOpenActionMenu(null); setPage(page - 1) }}
            >
              Trang trước
            </button>

            <span>
              Trang {page + 1} / {Math.max(totalPages, 1)} ({totalElements} booking)
            </span>

            <button
              type="button"
              disabled={page + 1 >= totalPages}
              onClick={() => { setOpenActionMenu(null); setPage(page + 1) }}
            >
              Trang sau
            </button>
          </div>
        </>
      )}

      {detailBooking && (
        <div className="booking-dialog-backdrop booking-detail-backdrop">
          <section className="booking-edit-dialog booking-detail-dialog" role="dialog" aria-modal="true" aria-labelledby="booking-detail-title">
            <header className="booking-detail-header">
              <div className="booking-detail-heading-copy">
                <span className="booking-detail-eyebrow">Chi tiết đặt phòng</span>
                <h3 id="booking-detail-title">{detailBooking.bookingCode}</h3>
                <p>{detailBooking.guestName}<span> · </span>Phòng {detailBooking.roomNumber ?? 'Chưa gán'}</p>
              </div>
              {detailData && <span className={`booking-status ${STATUS_CLASS_NAMES[detailData.status] ?? ''}`}>{STATUS_LABELS[detailData.status] ?? detailData.status}</span>}
              <button type="button" className="booking-dialog-close" aria-label="Đóng" onClick={() => setDetailBooking(null)}>×</button>
            </header>
            {detailLoading && <p>Đang tải thông tin từ máy chủ...</p>}
            {detailError && <div className="alert" role="alert">{detailError}</div>}
            {detailData && (
              <div className="booking-detail-content">
                <div className="booking-detail-columns">
                  <section className="booking-detail-section" aria-labelledby="booking-stay-title">
                    <h4 id="booking-stay-title">Thông tin lưu trú</h4>
                    <dl className="booking-detail-grid">
                      <div><dt>Loại phòng</dt><dd>{detailBooking.roomTypeNameSnapshot}</dd></div>
                      <div><dt>Phòng</dt><dd>{detailBooking.roomNumber ?? 'Chưa gán'}</dd></div>
                      <div><dt>Ngày nhận phòng</dt><dd>{formatDate(detailBooking.checkInDate)}</dd></div>
                      <div><dt>Ngày trả phòng</dt><dd>{formatDate(detailBooking.checkOutDate)}</dd></div>
                      {closedCheckoutDisplay && <>
                        <div><dt>Thời điểm checkout thực tế</dt><dd>{closedCheckoutDisplay.checkedOutAt ? new Date(closedCheckoutDisplay.checkedOutAt).toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' }) : 'Không có dữ liệu'}</dd></div>
                        {closedCheckoutDisplay.earlyCheckoutReason && <div className="booking-detail-wide"><dt>Lý do trả phòng sớm</dt><dd>{closedCheckoutDisplay.earlyCheckoutReason}</dd></div>}
                      </>}
                      <div><dt>Hạn giữ chỗ</dt><dd>{detailData.holdExpiresAt ? new Date(detailData.holdExpiresAt).toLocaleString('vi-VN') : 'Đã gỡ / không có'}</dd></div>
                    </dl>
                  </section>
                  <section className="booking-detail-section" aria-labelledby="booking-payment-title">
                    <h4 id="booking-payment-title">Thanh toán và tiền cọc</h4>
                    <dl className="booking-detail-grid">
                      <div><dt>Tổng tiền booking</dt><dd>{formatCurrency(detailBooking.totalAmount)}</dd></div>
                      {detailData.deposit ? <>
                        <div><dt>Số tiền cọc</dt><dd>{formatCurrency(detailData.deposit.amount)}</dd></div>
                        <div><dt>Phương thức</dt><dd>{detailData.deposit.paymentMethod === 'CASH' ? 'Tiền mặt' : detailData.deposit.paymentMethod === 'BANK_TRANSFER' ? 'Chuyển khoản' : detailData.deposit.paymentMethod}</dd></div>
                        <div><dt>Ngày nhận tiền</dt><dd>{formatDate(detailData.deposit.receivedDate)}</dd></div>
                        <div><dt>Mã giao dịch</dt><dd>{detailData.deposit.paymentReference || 'Không có'}</dd></div>
                        <div><dt>Người ghi nhận</dt><dd>{detailData.deposit.createdBy || 'Không có'}</dd></div>
                        <div><dt>Thời điểm ghi nhận</dt><dd>{detailData.deposit.createdAt ? new Date(detailData.deposit.createdAt).toLocaleString('vi-VN') : 'Không có'}</dd></div>
                      </> : <div className="booking-detail-wide"><dt>Tiền cọc</dt><dd>Booking chưa có tiền cọc được ghi nhận.</dd></div>}
                    </dl>
                    {detailData.deposit && <>
                      <div className="booking-deposit-total"><span>Tổng tiền cọc hiện tại</span><strong>{formatCurrency(Number(detailData.currentDepositTotal ?? detailData.deposit.amount))}</strong></div>
                      {(detailData.depositAdjustments ?? []).length > 0 && <div className="booking-adjustment-list">
                        {detailData.depositAdjustments.map((item) => <article className="booking-adjustment-item" key={item.id}>
                          <strong className={item.adjustmentType === 'TANG' ? 'deposit-increase' : 'deposit-decrease'}>{item.adjustmentType === 'TANG' ? 'Tăng' : 'Giảm'} {formatCurrency(item.amount)}</strong>
                          <span>{item.reason}</span><small>{item.createdBy || 'Không có'} · {new Date(item.createdAt).toLocaleString('vi-VN')}</small>
                        </article>)}
                      </div>}
                    </>}
                  </section>
                </div>
                <BookingSurchargeSection key={detailBooking.id} bookingId={detailBooking.id} canAdd={canAddSurcharge} />
                {detailData.registeredGuests && detailData.registeredGuests.length > 0 && (
                  <section className="booking-detail-section booking-detail-full" aria-labelledby="booking-guests-title">
                    <h4 id="booking-guests-title">Khách lưu trú</h4>
                    <ul className="booking-guest-list">
                      {detailData.registeredGuests.map((guest, index) => (
                        <li key={`${guest.primary ? 'primary' : 'guest'}-${index}`}>
                          {guest.fullName}{guest.primary ? ' (khách chính)' : ''}
                        </li>
                      ))}
                    </ul>
                  </section>
                )}
                {(detailBooking.status === 'DA_HUY' || detailBooking.status === 'DA_NHAN_PHONG') && <section className="booking-detail-section booking-detail-full booking-detail-alerts" aria-labelledby="booking-alerts-title">
                  <h4 id="booking-alerts-title">Cảnh báo nghiệp vụ</h4>
                  {detailBooking.status === 'DA_HUY' && <BookingCancellationInfoSection bookingId={detailBooking.id} />}
                  {detailBooking.status === 'DA_NHAN_PHONG' && <BookingCheckedInCancelNotice />}
                </section>}

                <section className="booking-detail-section booking-detail-full" aria-labelledby="booking-history-section-title">
                <h4 id="booking-history-section-title">Nhật ký chỉnh sửa Booking</h4>
                {detailData.history && detailData.history.length > 0 ? (
                  <div className="booking-history-list" id="booking-history-list">
                    {detailData.history.map((log) => (
                      <div key={log.id} className="booking-history-card" id={`booking-history-item-${log.id}`}>
                        <div className="booking-history-header">
                          <span className="booking-history-time">
                            ⏱️ {new Date(log.createdAt).toLocaleString('vi-VN')}
                          </span>
                          <span className="booking-history-actor">
                            👤 {log.actorName || log.actorEmail || 'Hệ thống'}
                            {log.actorEmail && log.actorName ? ` (${log.actorEmail})` : ''}
                          </span>
                        </div>
                        <div className="booking-history-diffs">
                          <div className="booking-history-diff-item">
                            <span className="diff-label">Ngày ở:</span>
                            <span className="diff-value">
                              {formatDate(log.oldCheckInDate)} – {formatDate(log.oldCheckOutDate)}
                              <span className="diff-arrow"> ➔ </span>
                              <strong>{formatDate(log.newCheckInDate)} – {formatDate(log.newCheckOutDate)}</strong>
                            </span>
                          </div>
                          <div className="booking-history-diff-item">
                            <span className="diff-label">Loại phòng:</span>
                            <span className="diff-value">
                              {log.oldRoomTypeName}
                              <span className="diff-arrow"> ➔ </span>
                              <strong>{log.newRoomTypeName}</strong>
                            </span>
                          </div>
                          <div className="booking-history-diff-item">
                            <span className="diff-label">Tổng tiền:</span>
                            <span className="diff-value">
                              {formatCurrency(log.oldTotalAmount)}
                              <span className="diff-arrow"> ➔ </span>
                              <strong className="diff-price">{formatCurrency(log.newTotalAmount)}</strong>
                            </span>
                          </div>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="booking-history-empty" id="booking-history-empty">Chưa có bản ghi chỉnh sửa thông tin booking.</p>
                )}
                </section>
                <section className="booking-detail-section booking-detail-full" aria-labelledby="booking-room-history-title">
                <h4 id="booking-room-history-title">Lịch sử đổi phòng</h4>
                {roomChangeHistory.length ? roomChangeHistory.map((item) => (
                  <div key={item.id} className="booking-history-card">
                    <div className="booking-history-header">
                      <span>{new Date(item.changedAt).toLocaleString('vi-VN')}</span>
                      <span>{item.actorName}</span>
                    </div>
                    <div>Phòng {item.oldRoomNumber} → Phòng {item.newRoomNumber}</div>
                    <div>Lý do: {item.reason}</div>
                  </div>
                )) : <p className="booking-history-empty">Chưa có lịch sử đổi phòng.</p>}
                </section>
              </div>
            )}
            <footer className="booking-detail-footer" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              {canNoShow && (detailBooking.status === 'CHO_XAC_NHAN' || detailBooking.status === 'DA_XAC_NHAN') && (() => {
                const eligibility = isNoShowEligible(detailBooking)
                return (
                  <button
                    type="button"
                    id={`booking-detail-no-show-btn-${detailBooking.id}`}
                    className={`booking-no-show-button ${eligibility.eligible ? 'is-active' : ''}`}
                    disabled={!eligibility.eligible}
                    title={eligibility.reason || 'Đánh dấu khách không đến'}
                    onClick={() => startNoShow(detailBooking)}
                    style={{
                      backgroundColor: eligibility.eligible ? '#d97706' : '#9ca3af',
                      color: '#ffffff',
                      border: 'none',
                      borderRadius: '6px',
                      padding: '8px 14px',
                      fontWeight: 500,
                      cursor: eligibility.eligible ? 'pointer' : 'not-allowed',
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '6px',
                    }}
                  >
                    <span aria-hidden="true">⏱</span> Khách không đến
                  </button>
                )
              })()}
              <button type="button" className="booking-cancel-button" onClick={() => setDetailBooking(null)}>Đóng</button>
            </footer>
          </section>
        </div>
      )}

      {noShowSuccess && (
        <div className="alert alert-success" role="status" style={{ margin: '16px 0', padding: '12px 16px', backgroundColor: '#ecfdf5', border: '1px solid #10b981', color: '#065f46', borderRadius: '6px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>{noShowSuccess}</span>
          <button type="button" onClick={() => setNoShowSuccess(null)} style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: '18px', color: '#065f46' }}>×</button>
        </div>
      )}

      {noShowBooking && (
        <div className="booking-dialog-backdrop">
          <section className="booking-edit-dialog" role="dialog" aria-modal="true" aria-labelledby="booking-no-show-title">
            <div className="booking-edit-heading">
              <div>
                <h3 id="booking-no-show-title">Xác nhận khách không đến (No-show)</h3>
                <p className="booking-edit-subheading">
                  Booking: <strong>{noShowBooking.bookingCode}</strong> — Khách: <strong>{noShowBooking.guestName}</strong>
                </p>
              </div>
              <button
                type="button"
                className="booking-dialog-close"
                aria-label="Đóng"
                disabled={noShowLoading}
                onClick={() => setNoShowBooking(null)}
              >×</button>
            </div>

            <div className="booking-dialog-body" style={{ padding: '16px 24px' }}>
              <p style={{ margin: '0 0 12px 0', lineHeight: 1.5 }}>
                Bạn có chắc chắn muốn đánh dấu khách không đến cho booking này?
              </p>
              <div style={{ backgroundColor: '#fffbeb', borderLeft: '4px solid #f59e0b', padding: '12px', borderRadius: '4px', fontSize: '14px', color: '#92400e' }}>
                <strong>Lưu ý:</strong>
                <ul style={{ margin: '4px 0 0 16px', padding: 0 }}>
                  <li>Trạng thái booking sẽ chuyển sang <strong>Không đến (NO_SHOW)</strong>.</li>
                  <li>Phòng <strong>{noShowBooking.roomNumber ? `Phòng ${noShowBooking.roomNumber}` : 'đã gán'}</strong> sẽ được tự động <strong>giải phóng</strong> ngay lập tức.</li>
                  <li>Hệ thống sẽ tăng số lần không đến (no-show count) của khách hàng và ghi nhận lịch sử thao tác lễ tân.</li>
                </ul>
              </div>
              {noShowError && (
                <div className="alert alert-error" role="alert" style={{ marginTop: '12px', color: '#dc2626' }}>
                  {noShowError}
                </div>
              )}
            </div>

            <footer className="booking-edit-footer" style={{ display: 'flex', justifyContent: 'flex-end', gap: '8px', padding: '16px 24px' }}>
              <button
                type="button"
                className="booking-cancel-button"
                disabled={noShowLoading}
                onClick={() => setNoShowBooking(null)}
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                id="confirm-no-show-btn"
                className="booking-confirm-submit"
                disabled={noShowLoading}
                onClick={handleConfirmNoShow}
                style={{ backgroundColor: '#d97706', color: '#ffffff', border: 'none', borderRadius: '6px', padding: '8px 16px', fontWeight: 600, cursor: noShowLoading ? 'not-allowed' : 'pointer' }}
              >
                {noShowLoading ? 'Đang xử lý…' : 'Xác nhận khách không đến'}
              </button>
            </footer>
          </section>
        </div>
      )}

      {!confirmingBooking && confirmError && <div className="alert" role="alert">{confirmError}</div>}
      {confirmingBooking && (
        <div className="booking-dialog-backdrop">
          <section className="booking-edit-dialog" role="dialog" aria-modal="true" aria-labelledby="booking-confirm-title">
            <div className="booking-edit-heading">
              <div>
                <h3 id="booking-confirm-title">Xác nhận đặt phòng và nhận cọc</h3>
                <p>{confirmingBooking.bookingCode} · {confirmingBooking.guestName}</p>
              </div>
              <button type="button" className="booking-dialog-close" aria-label="Đóng" disabled={confirmSaving} onClick={() => setConfirmingBooking(null)}>×</button>
            </div>
            <div className="booking-current-details">
              <strong>Thông tin đặt phòng</strong>
              <span>Loại phòng: {confirmingBooking.roomTypeNameSnapshot}</span>
              <span>Ngày nhận: {formatDate(confirmingBooking.checkInDate)}</span>
              <span>Ngày trả: {formatDate(confirmingBooking.checkOutDate)}</span>
              <span>Tổng tiền: {formatCurrency(confirmingBooking.totalAmount)}</span>
            </div>
            <form className="booking-edit-form" onSubmit={handleConfirmBooking}>
              <label>Số tiền cọc (VND)
                <strong>Tổng tiền đặt phòng: {formatCurrency(confirmingBooking.totalAmount)}</strong>
                <input type="number" min="1" max={confirmingBooking.totalAmount} step="1" required value={confirmForm.amount} onChange={(event) => setConfirmForm((f) => ({...f, amount: event.target.value}))} />
                {Number(confirmForm.amount) > confirmingBooking.totalAmount && (
                  <span className="alert" role="alert">Tiền cọc không được lớn hơn tổng tiền đặt phòng.</span>
                )}
              </label>
              <label>Phương thức thanh toán
                <select value={confirmForm.paymentMethod} onChange={(event) => setConfirmForm((f) => ({...f, paymentMethod: event.target.value as 'CASH' | 'BANK_TRANSFER'}))}>
                  <option value="CASH">Tiền mặt</option>
                  <option value="BANK_TRANSFER">Chuyển khoản</option>
                </select>
              </label>
              <label>Ngày nhận tiền
                <input type="date" required value={confirmForm.receivedDate} onChange={(event) => setConfirmForm((f) => ({...f, receivedDate: event.target.value}))} />
              </label>
              <label>Mã giao dịch {confirmForm.paymentMethod === 'BANK_TRANSFER' ? '(bắt buộc)' : '(không bắt buộc)'}
                <input type="text" required={confirmForm.paymentMethod === 'BANK_TRANSFER'} value={confirmForm.paymentReference} onChange={(event) => setConfirmForm((f) => ({...f, paymentReference: event.target.value}))} />
              </label>
              {confirmError && <div className="alert" role="alert">{confirmError}</div>}
              <div className="booking-edit-actions">
                <button type="button" className="booking-cancel-button" disabled={confirmSaving} onClick={() => setConfirmingBooking(null)}>Huỷ</button>
                <button type="submit" className="booking-save-button" disabled={confirmSaving || !Number.isSafeInteger(Number(confirmForm.amount)) || Number(confirmForm.amount) <= 0 || Number(confirmForm.amount) > confirmingBooking.totalAmount || !confirmForm.receivedDate || (confirmForm.paymentMethod === 'BANK_TRANSFER' && !confirmForm.paymentReference.trim())}>{confirmSaving ? 'Đang xác nhận...' : 'Xác nhận và lưu cọc'}</button>
              </div>
            </form>
          </section>
        </div>
      )}
              {cancellingBooking && (
        <CancelBookingDialog
          bookingCode={cancellingBooking.bookingCode}
          onClose={() => setCancellingBooking(null)}
          onCancelled={handleCancelled}
        />
      )}

      {roomBooking && (
        <div className="booking-dialog-backdrop">
          <section className="booking-edit-dialog" role="dialog" aria-modal="true" aria-labelledby="booking-room-title">
            <div className="booking-edit-heading">
              <div>
                <h3 id="booking-room-title">Chốt phòng cho {roomBooking.bookingCode}</h3>
                <p>Phòng đang giữ tạm: {roomBooking.roomNumber ?? 'Chưa có phòng'}</p>
              </div>
              <button type="button" className="booking-dialog-close" aria-label="Đóng" disabled={roomSaving} onClick={() => setRoomBooking(null)}>×</button>
            </div>
            {roomLoading ? <p>Đang tải phòng phù hợp...</p> : assignableRooms.length === 0 ? (
              <p role="status">Hiện không có phòng phù hợp trong toàn bộ thời gian lưu trú. Phòng có thể đang bảo trì, ngừng hoạt động hoặc đã được booking khác giữ.</p>
            ) : (
              <label className="booking-room-select">
                Phòng trống toàn bộ thời gian lưu trú
                <select value={selectedRoomId ?? ''} onChange={(event) => setSelectedRoomId(event.target.value ? Number(event.target.value) : null)}>
                  <option value="">Chọn phòng</option>
                  {assignableRooms.map((room) => <option key={room.id} value={room.id}>Phòng {room.roomNumber}{room.roomNumber === roomBooking.roomNumber ? ' · Phòng hiện tại' : ''} · Tầng {room.floor}</option>)}
                </select>
              </label>
            )}
            {roomError && <div className="alert" role="alert">{roomError}</div>}
            <div className="booking-dialog-actions booking-room-actions">
              <button type="button" className="booking-room-reload" disabled={roomLoading || roomSaving} onClick={() => void reloadAssignableRooms()}>
                <svg aria-hidden="true" viewBox="0 0 20 20" focusable="false"><path d="M16.5 8A6.75 6.75 0 0 0 4.7 5.4L3 7.2M3 7.2V3.8M3 7.2h3.4M3.5 12A6.75 6.75 0 0 0 15.3 14.6l1.7-1.8m0 0v3.4m0-3.4h-3.4" /></svg>
                {roomLoading ? 'Đang tải...' : 'Tải lại'}
              </button>
              <div className="booking-room-actions-end">
                <button type="button" className="booking-room-close" disabled={roomSaving} onClick={() => setRoomBooking(null)}>Đóng</button>
                <button type="button" className="booking-save-button booking-room-confirm" disabled={roomSaving || roomLoading || selectedRoomId === null} onClick={() => void confirmRoomSelection()}>
                  {roomSaving ? <><span className="booking-room-spinner" aria-hidden="true" />Đang chốt...</> : <><svg aria-hidden="true" viewBox="0 0 20 20" focusable="false"><path d="m4 10.5 4 4 8-9" /></svg>Chốt phòng</>}
                </button>
              </div>
            </div>
          </section>
        </div>
      )}

      {changeRoomBooking && (
        <div className="booking-dialog-backdrop">
          <section className="booking-edit-dialog" role="dialog" aria-modal="true" aria-labelledby="booking-change-room-title">
            <div className="booking-edit-heading">
              <div><h3 id="booking-change-room-title">Đổi phòng cho {changeRoomBooking.bookingCode}</h3><p>Phòng hiện tại: {changeRoomBooking.roomNumber}</p></div>
              <button type="button" className="booking-dialog-close" disabled={changeRoomSaving} onClick={() => setChangeRoomBooking(null)} aria-label="Đóng">×</button>
            </div>
            {roomLoading ? <p>Đang tải phòng khả dụng...</p> : (
              <label className="booking-room-select">Phòng mới
                <select value={selectedRoomId ?? ''} onChange={(event) => setSelectedRoomId(event.target.value ? Number(event.target.value) : null)}>
                  <option value="">Chọn phòng</option>
                  {assignableRooms.map((room) => <option key={room.id} value={room.id}>Phòng {room.roomNumber} · Tầng {room.floor}</option>)}
                </select>
              </label>
            )}
            {changeRoomBooking.status === 'DA_NHAN_PHONG' && <p>Chỉ chọn phòng trống sạch cùng loại. Hiện hệ thống yêu cầu phòng không trùng lịch trong toàn bộ kỳ lưu trú, kể cả phần ngày đã qua.</p>}
            {!roomLoading && assignableRooms.length === 0 && <p role="status">Không có phòng đủ điều kiện để đổi.</p>}
            <label className="booking-room-select">Lý do đổi phòng (bắt buộc)
              <textarea maxLength={500} value={changeRoomReason} onChange={(event) => setChangeRoomReason(event.target.value)} />
            </label>
            {roomError && <div className="alert" role="alert">{roomError}</div>}
            <div className="booking-dialog-actions">
              <button type="button" disabled={changeRoomSaving} onClick={() => setChangeRoomBooking(null)}>Đóng</button>
              <button type="button" className="booking-save-button" disabled={roomLoading || changeRoomSaving || !changeRoomReason.trim() || selectedRoomId === null} onClick={() => void submitRoomChange()}>
                {changeRoomSaving ? 'Đang đổi phòng...' : 'Xác nhận đổi phòng'}
              </button>
            </div>
          </section>
        </div>
      )}

      {editingBooking && (
        <div className="booking-dialog-backdrop booking-update-backdrop">
          <section
            className="booking-edit-dialog booking-update-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="booking-edit-title"
          >
            <div className="booking-edit-heading booking-update-header">
              <div className="booking-update-heading-copy">
                <h3 id="booking-edit-title">Cập nhật booking</h3>
                <p>{editingBooking.bookingCode} · {editingBooking.guestName}</p>
                <div className="booking-update-meta">
                  <span className={`booking-status ${STATUS_CLASS_NAMES[editingBooking.status] ?? ''}`}>{STATUS_LABELS[editingBooking.status] ?? editingBooking.status}</span>
                  {editingBooking.roomNumber && <span className="booking-update-room">Phòng {editingBooking.roomNumber}</span>}
                </div>
              </div>
              <button
                type="button"
                id="booking-dialog-close-btn"
                className="booking-dialog-close"
                aria-label="Đóng"
                onClick={() => setEditingBooking(null)}
                disabled={editSaving}
              >
                ×
              </button>
            </div>

            <form className="booking-update-layout" id="booking-edit-form" onSubmit={handleUpdateBooking}>
              <div className="booking-update-content">
              <section className="booking-update-section booking-update-stay" aria-labelledby="booking-stay-title">
                <div className="booking-update-section-heading"><h4 id="booking-stay-title">Chỉnh sửa lưu trú</h4></div>
                <div className="booking-edit-form booking-update-form">
                  <label htmlFor="booking-edit-checkin">Ngày nhận phòng<input id="booking-edit-checkin" type="date" required min={new Date().toLocaleDateString('en-CA')} value={editForm.checkInDate} onChange={(event) => updateEditForm((current) => ({ ...current, checkInDate: event.target.value }))} /></label>
                  <label htmlFor="booking-edit-checkout">Ngày trả phòng<input id="booking-edit-checkout" type="date" required min={editForm.checkInDate || undefined} value={editForm.checkOutDate} onChange={(event) => updateEditForm((current) => ({ ...current, checkOutDate: event.target.value }))} /></label>
                  <label className="booking-update-roomtype" htmlFor="booking-edit-roomtype">Loại phòng mới<select id="booking-edit-roomtype" required value={editForm.roomTypeId} disabled={roomTypesLoading} onChange={(event) => updateEditForm((current) => ({ ...current, roomTypeId: event.target.value }))}>
                    <option value="">{roomTypesLoading ? 'Đang tải loại phòng...' : 'Chọn loại phòng'}</option>
                    {roomTypes.filter((roomType) => roomType.active || String(roomType.id) === editForm.roomTypeId).map((roomType) => <option key={roomType.id} value={roomType.id}>{roomType.name}{roomType.active ? '' : ' (Ngừng bán)'}</option>)}
                  </select></label>
                </div>
              </section>
              <div className="booking-current-details booking-update-current" id="booking-current-details">
              <strong>Thông tin hiện tại</strong>
              <span>Ngày nhận: {formatDate(editingBooking.checkInDate)}</span>
              <span>Ngày trả: {formatDate(editingBooking.checkOutDate)}</span>
              <span>Loại phòng: {editingBooking.roomTypeNameSnapshot}</span>
              <span>Tổng tiền: {formatCurrency(editingBooking.totalAmount)}</span>
            </div>

            <section className="booking-deposit-panel booking-update-section" aria-labelledby="booking-deposit-title">
              <h4 id="booking-deposit-title">Thông tin tiền cọc</h4>
              {editDetailLoading && <p>Đang tải thông tin tiền cọc...</p>}
              {editDetailError && <div className="alert" role="alert">{editDetailError}</div>}
              {editDetailData && (editDetailData.deposit ? <>
                <dl className="booking-detail-grid booking-update-deposit-stats">
                  <div><dt>Tổng tiền booking</dt><dd>{formatCurrency(editingBooking.totalAmount)}</dd></div>
                  <div><dt>Tiền cọc gốc</dt><dd>{formatCurrency(editDetailData.deposit.amount)}</dd></div>
                  <div><dt>Tổng tiền cọc hiện tại</dt><dd>{formatCurrency(Number(editDetailData.currentDepositTotal ?? editDetailData.deposit.amount))}</dd></div>
                </dl>
                <h5>Lịch sử điều chỉnh</h5>
                {(editDetailData.depositAdjustments ?? []).length ? <div className="booking-adjustment-list">
                  {editDetailData.depositAdjustments.map((item) => <article className="booking-adjustment-item" key={item.id}>
                    <strong className={item.adjustmentType === 'TANG' ? 'deposit-increase' : 'deposit-decrease'}>{item.adjustmentType === 'TANG' ? 'TĂNG' : 'GIẢM'} {formatCurrency(item.amount)}</strong>
                    <span>{item.reason}</span>
                    <small>{item.createdBy || 'Không rõ người thực hiện'} · {item.createdAt ? new Date(item.createdAt).toLocaleString('vi-VN') : 'Không có thời điểm'}</small>
                  </article>)}
                </div> : <p>Chưa có bút toán điều chỉnh.</p>}
                {canAdjustDeposit && editDetailData.status === 'DA_XAC_NHAN' && <button type="button" className="booking-save-button" disabled={editDetailLoading} onClick={() => { setAdjustmentError(null); setAdjustmentOpen(true) }}>Điều chỉnh tiền cọc</button>}
              </> : <p>Booking chưa có tiền cọc gốc được ghi nhận.</p>)}
            </section>

              {previewLoading && (
                <div className="booking-preview-card" id="booking-preview-card">
                  <div className="preview-loading">Đang kiểm tra phòng trống và tính lại tiền...</div>
                </div>
              )}

              {previewError && !previewLoading && (
                <div className="booking-preview-card unavailable" id="booking-preview-card">
                  <div className="preview-warning" id="booking-preview-error">⚠️ {previewError}</div>
                </div>
              )}

              {preview && !previewLoading && (
                <div
                  className={`booking-preview-card ${preview.available ? 'available' : 'unavailable'}`}
                  id="booking-preview-card"
                >
                  <div className="preview-row">
                    <strong>Xem trước thay đổi</strong>
                    <span
                      id="preview-room-status"
                      className={`preview-badge ${preview.available ? 'available' : 'sold-out'}`}
                    >
                      {preview.available
                        ? `🟢 Còn ${preview.availableRooms} phòng trống`
                        : '🔴 Hết phòng trong thời gian này'}
                    </span>
                  </div>

                  <div className="preview-row">
                    <span>Thời gian ở:</span>
                    <span>
                      <strong>{preview.numberOfNights} đêm</strong> ({formatDate(preview.checkInDate)} – {formatDate(preview.checkOutDate)})
                    </span>
                  </div>

                  <div className="preview-row">
                    <span>Loại phòng:</span>
                    <span>{preview.roomTypeName}</span>
                  </div>

                  <div className="preview-row">
                    <span>Tổng tiền mới:</span>
                    <div>
                      <span className="preview-price" id="preview-total-amount">
                        {formatCurrency(preview.totalAmount)}
                      </span>
                      {preview.totalAmount !== editingBooking.totalAmount && (
                        <span className="preview-price-old">
                          {formatCurrency(editingBooking.totalAmount)}
                        </span>
                      )}
                    </div>
                  </div>
                  {preview.totalDifference !== 0 && (
                    <div className="preview-row">
                      <span>Chênh lệch so với tổng cũ:</span>
                      <span id="preview-total-difference">
                        {preview.totalDifference > 0 ? '+' : '−'}{formatCurrency(Math.abs(preview.totalDifference))}
                      </span>
                    </div>
                  )}

                  <div className="preview-row">
                    <span>Tiền cọc đã ghi nhận:</span>
                    <span id="preview-deposit-amount">
                      {preview.depositAmount > 0 ? formatCurrency(preview.depositAmount) : 'Chưa ghi nhận'}
                    </span>
                  </div>

                  <div className="preview-row">
                    <span>Còn phải thu:</span>
                    <strong id="preview-remaining-amount">{formatCurrency(Math.max(preview.remainingAmount, 0))}</strong>
                  </div>

                  {preview.remainingAmount < 0 && (
                    <p className="preview-warning" id="preview-deposit-over-msg">
                      ⚠️ Tiền cọc đã ghi nhận lớn hơn tổng tiền mới. Vui lòng ghi bút toán điều chỉnh giảm cọc trước khi đổi.
                    </p>
                  )}

                  {!preview.available && (
                    <p className="preview-warning" id="preview-sold-out-msg">
                      ⚠️ Loại phòng này đã hết phòng trong khoảng thời gian đã chọn. Không thể lưu thay đổi.
                    </p>
                  )}
                </div>
              )}

              </div>
              {editError && <div className="alert" id="booking-edit-error" role="alert">{editError}</div>}
              <div className="booking-edit-actions booking-update-footer">
                <button
                  type="button"
                  id="booking-edit-cancel-btn"
                  className="booking-cancel-button"
                  onClick={() => setEditingBooking(null)}
                  disabled={editSaving}
                >
                  Huỷ
                </button>
                <button
                  type="submit"
                  id="booking-edit-submit-btn"
                  className="booking-save-button booking-update-submit"
                  disabled={
                    editSaving ||
                    roomTypesLoading ||
                    previewLoading ||
                    (preview !== null && (!preview.available || preview.remainingAmount < 0))
                  }
                >
                  {editSaving ? 'Đang lưu...' : 'Lưu thay đổi'}
                </button>
              </div>
            </form>
          </section>
        </div>
      )}
      {adjustmentOpen && editingBooking && editDetailData?.deposit && (
        <div className="booking-dialog-backdrop booking-adjustment-backdrop">
          <section className="booking-edit-dialog booking-adjustment-dialog" role="dialog" aria-modal="true" aria-labelledby="booking-adjustment-title">
            <div className="booking-edit-heading"><div><h3 id="booking-adjustment-title">Điều chỉnh tiền cọc</h3><p>{editingBooking.bookingCode} · {editingBooking.guestName}</p></div>
              <button type="button" className="booking-dialog-close" aria-label="Đóng" disabled={adjustmentSaving} onClick={() => setAdjustmentOpen(false)}>×</button></div>
            <form className="booking-edit-form" onSubmit={handleCreateDepositAdjustment}>
              <label>Loại điều chỉnh<select value={adjustmentForm.type} disabled={adjustmentSaving} onChange={(event) => setAdjustmentForm((f) => ({ ...f, type: event.target.value as 'TANG' | 'GIAM' }))}><option value="TANG">TĂNG</option><option value="GIAM">GIẢM</option></select></label>
              <label>Số tiền điều chỉnh<input type="number" min="1" step="1" required value={adjustmentForm.amount} disabled={adjustmentSaving} onChange={(event) => setAdjustmentForm((f) => ({ ...f, amount: event.target.value }))} /></label>
              <label>Lý do điều chỉnh<textarea required maxLength={500} rows={3} value={adjustmentForm.reason} disabled={adjustmentSaving} onChange={(event) => setAdjustmentForm((f) => ({ ...f, reason: event.target.value }))} /></label>
              <div className="booking-adjustment-projection">Tổng cọc dự kiến: <strong>{formatCurrency(Number(editDetailData.currentDepositTotal ?? editDetailData.deposit.amount) + (adjustmentForm.type === 'TANG' ? 1 : -1) * (Number(adjustmentForm.amount) || 0))}</strong><small>Không được thấp hơn 0 hoặc vượt tổng tiền booking ({formatCurrency(editingBooking.totalAmount)}).</small></div>
              {adjustmentError && <div className="alert" role="alert">{adjustmentError}</div>}
              <div className="booking-edit-actions"><button type="button" className="booking-cancel-button" disabled={adjustmentSaving} onClick={() => setAdjustmentOpen(false)}>Hủy</button><button type="submit" className="booking-save-button" disabled={adjustmentSaving || !Number.isSafeInteger(Number(adjustmentForm.amount)) || Number(adjustmentForm.amount) <= 0 || !adjustmentForm.reason.trim() || adjustmentForm.reason.length > 500}>{adjustmentSaving ? 'Đang lưu...' : 'Lưu điều chỉnh'}</button></div>
            </form>
          </section>
        </div>
      )}
    </section>
  )
}
