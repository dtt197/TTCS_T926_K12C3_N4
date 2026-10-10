import { canChangeBookingRoom, roomChangeOptions } from './bookingRoomChangeActions'
import './BookingListPage.css'
import { ApiRequestError } from '../services/apiClient'
import type {
  BookingStatus,
  BookingListItem,
  BookingChangePreview,
  AssignableRoom,
  BookingRoomChangeHistory,
} from '../types/booking'
import { useEffect, useState, type FormEvent } from 'react'
import type { BookingDetailResponse } from '../services/bookingService'
import { createBookingDepositAdjustment } from '../services/bookingService'
import { hasPermission } from '../permissions/rolePermissions'
import {
  confirmBooking,
  cancelBooking,
  getBookingDetails,
  searchBookings,
  updateBooking,
  previewBookingChange,
  getAvailableRooms,
  assignBookingRoom,
  changeBookingRoom,
  getBookingRoomChangeHistory,
} from '../services/bookingService'
import { getRoomTypes } from '../services/roomTypeService'
import type { RoomType } from '../types/roomType'
import { RoomShortageAlertSection } from '../components/RoomShortageAlertSection'
import { BookingCancellationInfoSection } from '../components/BookingCancellationInfoSection'
import { confirmRoomSelection as runRoomAssignment, reloadAssignableRooms as runRoomReload } from './bookingRoomActions.ts'
import { submitRoomChange as runRoomChange } from './bookingRoomChangeActions.ts'

const STATUS_LABELS: Record<string, string> = {
  CHO_XAC_NHAN: 'Chờ xác nhận',
  DA_XAC_NHAN: 'Đã xác nhận',
  DA_HUY: 'Đã huỷ',
  DA_NHAN_PHONG: 'Đã nhận phòng',
  DA_TRA_PHONG: 'Đã trả phòng',
  DA_HET_HAN: 'Đã hết hạn',
}

const STATUS_CLASS_NAMES: Record<string, string> = {
  CHO_XAC_NHAN: 'pending',
  DA_XAC_NHAN: 'confirmed',
  DA_HUY: 'cancelled',
  DA_NHAN_PHONG: 'checked-in',
  DA_TRA_PHONG: 'checked-out',
  DA_HET_HAN: 'cancelled',
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
    // S3-02 Lát 3: huỷ booking
  const [cancellingBooking, setCancellingBooking] = useState<BookingListItem | null>(null)
  const [cancelReason, setCancelReason] = useState('')
  const [cancelSaving, setCancelSaving] = useState(false)
  const [cancelError, setCancelError] = useState<string | null>(null)
  const [editForm, setEditForm] = useState({
    checkInDate: '',
    checkOutDate: '',
    roomTypeId: '',
  })
  const [preview, setPreview] = useState<BookingChangePreview | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [previewError, setPreviewError] = useState<string | null>(null)
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

  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [appliedFilters, setAppliedFilters] = useState(EMPTY_FILTERS)

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

  useEffect(() => {
    if (!editingBooking) {
      setPreview(null)
      setPreviewError(null)
      setPreviewLoading(false)
      return
    }

    if (!editForm.checkInDate || !editForm.checkOutDate || !editForm.roomTypeId) {
      setPreview(null)
      setPreviewError(null)
      setPreviewLoading(false)
      return
    }

    if (editForm.checkInDate >= editForm.checkOutDate) {
      setPreview(null)
      setPreviewError('Ngày trả phòng phải sau ngày nhận phòng.')
      setPreviewLoading(false)
      return
    }

    let cancelled = false
    setPreviewLoading(true)
    setPreviewError(null)

    const timer = setTimeout(() => {
      previewBookingChange(editingBooking.id, {
        checkInDate: editForm.checkInDate,
        checkOutDate: editForm.checkOutDate,
        roomTypeId: Number(editForm.roomTypeId),
      })
        .then((result) => {
          if (!cancelled) {
            setPreview(result)
            setPreviewError(null)
          }
        })
        .catch((err: unknown) => {
          if (!cancelled) {
            setPreview(null)
            setPreviewError(
              err instanceof Error
                ? err.message
                : 'Không thể tính toán giá phòng và kiểm tra phòng trống.',
            )
          }
        })
        .finally(() => {
          if (!cancelled) {
            setPreviewLoading(false)
          }
        })
    }, 250)

    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [
    editingBooking?.id,
    editForm.checkInDate,
    editForm.checkOutDate,
    editForm.roomTypeId,
  ])

  function handleFilterSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

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
    setCancelReason('')
    setCancelError(null)
  }

  async function handleCancelBooking(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!cancellingBooking || cancelSaving) return
    const reason = cancelReason.trim()
    if (!reason) {
      setCancelError('Vui lòng nhập lý do huỷ booking.')
      return
    }
    setCancelSaving(true)
    setCancelError(null)
    try {
      await cancelBooking(cancellingBooking.id, reason)
      setEditSuccess(`Đã huỷ booking ${cancellingBooking.bookingCode}. Phòng đã được trả lại để bán.`)
      setCancellingBooking(null)
      setRefreshVersion((version) => version + 1)
    } catch (err) {
      setCancelError(err instanceof Error ? err.message : 'Không thể huỷ booking.')
    } finally {
      setCancelSaving(false)
    }
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

  function startEditing(booking: BookingListItem) {
    setEditingBooking(booking)
    setEditForm({
      checkInDate: booking.checkInDate,
      checkOutDate: booking.checkOutDate,
      roomTypeId: booking.roomTypeId === null ? '' : String(booking.roomTypeId),
    })
    setPreview(null)
    setPreviewError(null)
    setPreviewLoading(false)
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

  return (
    <section className="panel booking-page">
      <div className="panel-heading">
        <div>
          <h2>Booking mới</h2>
          <p>Danh sách được sắp xếp theo thời điểm tạo mới nhất.</p>
        </div>
      </div>

      <RoomShortageAlertSection />

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
                    <td>
                      {canManage && booking.status === 'CHO_XAC_NHAN' && !booking.holdExpired && (
                        <button type="button" className="booking-confirm-button" onClick={() => startConfirming(booking)}>
                          Xác nhận
                        </button>
                      )}
                      {canManage && booking.status === 'DA_XAC_NHAN' && !booking.roomConfirmedAt && (
                        <button type="button" className="booking-edit-button" onClick={() => void startRoomSelection(booking)}>
                          Chốt phòng
                        </button>
                      )}
                      {canChangeBookingRoom(role, booking, new Date().toLocaleDateString('sv-SE', { timeZone: 'Asia/Ho_Chi_Minh' })) && (
                        <button type="button" className="booking-edit-button" onClick={() => void openRoomChange(booking)}>Đổi phòng</button>
                      )}
                      <button type="button" className="booking-detail-button" onClick={() => void openDetails(booking)}>
                        Chi tiết
                      </button>
                      {canManage && (
                        <button
                          type="button"
                          id={`booking-edit-btn-${booking.id}`}
                          className="booking-edit-button"
                          onClick={() => startEditing(booking)}
                        >
                          Cập nhật
                        </button>
                      )}
                        {canCancel && (booking.status === 'CHO_XAC_NHAN' || booking.status === 'DA_XAC_NHAN') && (
                        <button type="button" className="booking-cancel-booking-button" onClick={() => startCancelling(booking)}>
                          Huỷ booking
                        </button>
                      )}
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
              onClick={() => setPage(page - 1)}
            >
              Trang trước
            </button>

            <span>
              Trang {page + 1} / {Math.max(totalPages, 1)} ({totalElements} booking)
            </span>

            <button
              type="button"
              disabled={page + 1 >= totalPages}
              onClick={() => setPage(page + 1)}
            >
              Trang sau
            </button>
          </div>
        </>
      )}

      {detailBooking && (
        <div className="booking-dialog-backdrop">
          <section className="booking-edit-dialog booking-detail-dialog" role="dialog" aria-modal="true" aria-labelledby="booking-detail-title">
            <div className="booking-edit-heading">
              <div>
                <h3 id="booking-detail-title">Chi tiết đặt phòng</h3>
                <p>{detailBooking.bookingCode} · {detailBooking.guestName}</p>
              </div>
              <button type="button" className="booking-dialog-close" aria-label="Đóng" onClick={() => setDetailBooking(null)}>×</button>
            </div>
            {detailLoading && <p>Đang tải thông tin từ máy chủ...</p>}
            {detailError && <div className="alert" role="alert">{detailError}</div>}
            {detailData && (
              <div className="booking-detail-content">
                <h4>Thông tin đặt phòng</h4>
                <dl className="booking-detail-grid">
                  <div><dt>Mã booking</dt><dd>{detailBooking.bookingCode}</dd></div>
                  <div><dt>Trạng thái</dt><dd>{STATUS_LABELS[detailData.status] ?? detailData.status}</dd></div>
                  <div><dt>Loại phòng</dt><dd>{detailBooking.roomTypeNameSnapshot}</dd></div>
                  <div><dt>Phòng</dt><dd>{detailBooking.roomNumber ?? 'Chưa gán'}</dd></div>
                  <div><dt>Tổng tiền</dt><dd>{formatCurrency(detailBooking.totalAmount)}</dd></div>
                  <div><dt>Ngày nhận phòng</dt><dd>{formatDate(detailBooking.checkInDate)}</dd></div>
                  <div><dt>Ngày trả phòng</dt><dd>{formatDate(detailBooking.checkOutDate)}</dd></div>
                  <div><dt>Hạn giữ chỗ</dt><dd>{detailData.holdExpiresAt ? new Date(detailData.holdExpiresAt).toLocaleString('vi-VN') : 'Đã gỡ / không có'}</dd></div>
                </dl>
                {detailData.registeredGuests && detailData.registeredGuests.length > 0 && (
                  <>
                    <h4>Khách đã đăng ký lưu trú</h4>
                    <ul>
                      {detailData.registeredGuests.map((guest, index) => (
                        <li key={`${guest.primary ? 'primary' : 'guest'}-${index}`}>
                          {guest.fullName}{guest.primary ? ' (khách chính)' : ''}
                        </li>
                      ))}
                    </ul>
                  </>
                )}
                {detailBooking.status === 'DA_HUY' && <BookingCancellationInfoSection bookingId={detailBooking.id} />}
                <h4>Thông tin tiền cọc</h4>
                {detailData.deposit ? (
                  <dl className="booking-detail-grid">
                    <div><dt>Số tiền cọc</dt><dd>{formatCurrency(detailData.deposit.amount)}</dd></div>
                    <div><dt>Phương thức</dt><dd>{detailData.deposit.paymentMethod === 'CASH' ? 'Tiền mặt' : detailData.deposit.paymentMethod === 'BANK_TRANSFER' ? 'Chuyển khoản' : detailData.deposit.paymentMethod}</dd></div>
                    <div><dt>Ngày nhận tiền</dt><dd>{formatDate(detailData.deposit.receivedDate)}</dd></div>
                    <div><dt>Mã giao dịch</dt><dd>{detailData.deposit.paymentReference || 'Không có'}</dd></div>
                    <div><dt>Người ghi nhận</dt><dd>{detailData.deposit.createdBy || 'Không có'}</dd></div>
                    <div><dt>Thời điểm ghi nhận</dt><dd>{detailData.deposit.createdAt ? new Date(detailData.deposit.createdAt).toLocaleString('vi-VN') : 'Không có'}</dd></div>
                  </dl>
                ) : <p>Booking chưa có tiền cọc được ghi nhận.</p>}

                <h4 id="booking-history-section-title">Lịch sử thay đổi (Audit Log)</h4>
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
                  <p className="booking-history-empty" id="booking-history-empty">Chưa có lịch sử thay đổi nào cho booking này.</p>
                )}
                <h4>Lịch sử đổi phòng</h4>
                {roomChangeHistory.length ? roomChangeHistory.map((item) => (
                  <div key={item.id} className="booking-history-card">
                    <div className="booking-history-header">
                      <span>{new Date(item.changedAt).toLocaleString('vi-VN')}</span>
                      <span>{item.actorName}</span>
                    </div>
                    <div>Phòng {item.oldRoomNumber} → Phòng {item.newRoomNumber}</div>
                    <div>Lý do: {item.reason}</div>
                  </div>
                )) : <p>Chưa có lịch sử đổi phòng.</p>}
              </div>
            )}
            <div className="booking-edit-actions">
              <button type="button" className="booking-cancel-button" onClick={() => setDetailBooking(null)}>Đóng</button>
            </div>
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
        <div className="booking-dialog-backdrop">
          <section className="booking-edit-dialog" role="dialog" aria-modal="true" aria-labelledby="booking-cancel-title">
            <div className="booking-edit-heading">
              <div>
                <h3 id="booking-cancel-title">Huỷ booking</h3>
                <p>{cancellingBooking.bookingCode} · {cancellingBooking.guestName}</p>
              </div>
              <button type="button" className="booking-dialog-close" aria-label="Đóng" disabled={cancelSaving} onClick={() => setCancellingBooking(null)}>×</button>
            </div>
            <div className="booking-current-details">
              <strong>Thông tin đặt phòng</strong>
              <span>Loại phòng: {cancellingBooking.roomTypeNameSnapshot}</span>
              <span>Phòng: {cancellingBooking.roomNumber ?? 'Chưa gán'}</span>
              <span>Ngày nhận: {formatDate(cancellingBooking.checkInDate)}</span>
              <span>Ngày trả: {formatDate(cancellingBooking.checkOutDate)}</span>
            </div>
            <form className="booking-edit-form" onSubmit={handleCancelBooking}>
              <label>Lý do huỷ (bắt buộc)
                <textarea required maxLength={500} rows={3} value={cancelReason} onChange={(event) => setCancelReason(event.target.value)} placeholder="Ví dụ: khách báo đổi kế hoạch" />
              </label>
              <p className="booking-cancel-note">Sau khi huỷ, phòng được trả lại để bán ngay. Hoàn cọc (nếu có) xử lý theo chính sách huỷ.</p>
              {cancelError && <div className="alert" role="alert">{cancelError}</div>}
              <div className="booking-edit-actions">
                <button type="button" className="booking-cancel-button" disabled={cancelSaving} onClick={() => setCancellingBooking(null)}>Đóng</button>
                <button type="submit" className="booking-save-button booking-save-button--danger" disabled={cancelSaving || !cancelReason.trim()}>{cancelSaving ? 'Đang huỷ...' : 'Xác nhận huỷ booking'}</button>
              </div>
            </form>
          </section>
        </div>
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
            <div className="booking-dialog-actions">
              <button type="button" disabled={roomLoading || roomSaving} onClick={() => void reloadAssignableRooms()}>Tải lại danh sách phòng</button>
              <button type="button" disabled={roomSaving} onClick={() => setRoomBooking(null)}>Đóng</button>
              <button type="button" className="booking-save-button" disabled={roomSaving || roomLoading || selectedRoomId === null} onClick={() => void confirmRoomSelection()}>
                {roomSaving ? 'Đang xác nhận...' : 'Xác nhận chọn phòng'}
              </button>
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
        <div className="booking-dialog-backdrop">
          <section
            className="booking-edit-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="booking-edit-title"
          >
            <div className="booking-edit-heading">
              <div>
                <h3 id="booking-edit-title">Cập nhật booking</h3>
                <p>{editingBooking.bookingCode} · {editingBooking.guestName}</p>
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

            <div className="booking-current-details" id="booking-current-details">
              <strong>Thông tin hiện tại</strong>
              <span>Ngày nhận: {formatDate(editingBooking.checkInDate)}</span>
              <span>Ngày trả: {formatDate(editingBooking.checkOutDate)}</span>
              <span>Loại phòng: {editingBooking.roomTypeNameSnapshot}</span>
              <span>Tổng tiền: {formatCurrency(editingBooking.totalAmount)}</span>
            </div>

            <section className="booking-deposit-panel" aria-labelledby="booking-deposit-title">
              <h4 id="booking-deposit-title">Thông tin tiền cọc</h4>
              {editDetailLoading && <p>Đang tải thông tin tiền cọc...</p>}
              {editDetailError && <div className="alert" role="alert">{editDetailError}</div>}
              {editDetailData && (editDetailData.deposit ? <>
                <dl className="booking-detail-grid">
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

            <form className="booking-edit-form" id="booking-edit-form" onSubmit={handleUpdateBooking}>
              <label htmlFor="booking-edit-checkin">
                Ngày nhận phòng
                <input
                  id="booking-edit-checkin"
                  type="date"
                  required
                  value={editForm.checkInDate}
                  onChange={(event) =>
                    setEditForm((current) => ({
                      ...current,
                      checkInDate: event.target.value,
                    }))
                  }
                />
              </label>
              <label htmlFor="booking-edit-checkout">
                Ngày trả phòng
                <input
                  id="booking-edit-checkout"
                  type="date"
                  required
                  min={editForm.checkInDate || undefined}
                  value={editForm.checkOutDate}
                  onChange={(event) =>
                    setEditForm((current) => ({
                      ...current,
                      checkOutDate: event.target.value,
                    }))
                  }
                />
              </label>
              <label htmlFor="booking-edit-roomtype">
                Loại phòng mới
                <select
                  id="booking-edit-roomtype"
                  required
                  value={editForm.roomTypeId}
                  disabled={roomTypesLoading}
                  onChange={(event) =>
                    setEditForm((current) => ({
                      ...current,
                      roomTypeId: event.target.value,
                    }))
                  }
                >
                  <option value="">
                    {roomTypesLoading ? 'Đang tải loại phòng...' : 'Chọn loại phòng'}
                  </option>
                  {roomTypes
                    .filter(
                      (roomType) =>
                        roomType.active ||
                        String(roomType.id) === editForm.roomTypeId,
                    )
                    .map((roomType) => (
                      <option key={roomType.id} value={roomType.id}>
                        {roomType.name}{roomType.active ? '' : ' (Ngừng bán)'}
                      </option>
                    ))}
                </select>
              </label>

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

                  {!preview.available && (
                    <p className="preview-warning" id="preview-sold-out-msg">
                      ⚠️ Loại phòng này đã hết phòng trong khoảng thời gian đã chọn. Không thể lưu thay đổi.
                    </p>
                  )}
                </div>
              )}

              {editError && <div className="alert" id="booking-edit-error" role="alert">{editError}</div>}
              <div className="booking-edit-actions">
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
                  className="booking-save-button"
                  disabled={
                    editSaving ||
                    roomTypesLoading ||
                    previewLoading ||
                    (preview !== null && !preview.available)
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
