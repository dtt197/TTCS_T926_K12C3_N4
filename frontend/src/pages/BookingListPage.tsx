import './BookingListPage.css'
import type {
  BookingStatus,
  BookingListItem,
  BookingChangePreview,
} from '../types/booking'
import { useEffect, useState, type FormEvent } from 'react'
import {
  searchBookings,
  updateBooking,
  previewBookingChange,
} from '../services/bookingService'
import { getRoomTypes } from '../services/roomTypeService'
import type { RoomType } from '../types/roomType'
import { RoomShortageAlertSection } from '../components/RoomShortageAlertSection'

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

export function BookingListPage() {
  const [bookings, setBookings] = useState<BookingListItem[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [totalElements, setTotalElements] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [editingBooking, setEditingBooking] = useState<BookingListItem | null>(null)
  const [roomTypes, setRoomTypes] = useState<RoomType[]>([])
  const [roomTypesLoading, setRoomTypesLoading] = useState(false)
  const [editSaving, setEditSaving] = useState(false)
  const [editError, setEditError] = useState<string | null>(null)
  const [editSuccess, setEditSuccess] = useState<string | null>(null)
  const [refreshVersion, setRefreshVersion] = useState(0)
  const [editForm, setEditForm] = useState({
    checkInDate: '',
    checkOutDate: '',
    roomTypeId: '',
  })
  const [preview, setPreview] = useState<BookingChangePreview | null>(null)
  const [previewLoading, setPreviewLoading] = useState(false)
  const [previewError, setPreviewError] = useState<string | null>(null)

  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [appliedFilters, setAppliedFilters] = useState(EMPTY_FILTERS)

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
                      <button
                        type="button"
                        id={`booking-edit-btn-${booking.id}`}
                        className="booking-edit-button"
                        onClick={() => startEditing(booking)}
                      >
                        Cập nhật
                      </button>
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
    </section>
  )
}