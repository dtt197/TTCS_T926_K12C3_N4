import { useEffect, useState, useCallback } from 'react'
import type { RoomShortageAlert, ShortageBooking } from '../types/roomShortage'
import { getRoomShortageAlerts, getShortageBookings } from '../services/roomShortageService'
import './RoomShortageAlertSection.css'

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

const SOURCE_LABELS: Record<string, string> = {
  TRUC_TUYEN: 'Trực tuyến',
  TAI_QUAY: 'Tại quầy',
}

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const parts = dateStr.split('-')
  if (parts.length === 3) {
    const [year, month, day] = parts
    return `${day}/${month}/${year}`
  }
  return dateStr
}

function formatDateTime(isoStr: string): string {
  if (!isoStr) return ''
  try {
    const d = new Date(isoStr)
    return d.toLocaleString('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh' })
  } catch {
    return isoStr
  }
}

type SelectedAlert = {
  date: string
  roomTypeCode: string
  roomTypeName: string
  bookingCount: number
  availableRooms: number
}

export function RoomShortageAlertSection() {
  const [alerts, setAlerts] = useState<RoomShortageAlert[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  // S3-08: Trạng thái cho danh sách booking khi bấm vào cảnh báo
  const [selectedAlert, setSelectedAlert] = useState<SelectedAlert | null>(null)
  const [bookings, setBookings] = useState<ShortageBooking[]>([])
  const [bookingsLoading, setBookingsLoading] = useState(false)
  const [bookingsError, setBookingsError] = useState<string | null>(null)

  const fetchAlerts = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getRoomShortageAlerts()
      // Sắp xếp ngày gần nhất trước (date ASC), cùng ngày theo tên loại phòng
      const sorted = [...(data || [])].sort((a, b) => {
        const dateCompare = a.date.localeCompare(b.date)
        if (dateCompare !== 0) return dateCompare
        return a.roomTypeName.localeCompare(b.roomTypeName, 'vi')
      })
      setAlerts(sorted)
    } catch (err) {
      setError(
        err instanceof Error
          ? err.message
          : 'Không thể tải danh sách cảnh báo thiếu phòng.',
      )
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void fetchAlerts()
  }, [fetchAlerts])

  // S3-08: Mở danh sách booking liên quan khi bấm vào cảnh báo
  const handleAlertClick = useCallback(async (alert: RoomShortageAlert) => {
    setSelectedAlert({
      date: alert.date,
      roomTypeCode: alert.roomTypeCode,
      roomTypeName: alert.roomTypeName,
      bookingCount: alert.bookingCount,
      availableRooms: alert.availableRooms,
    })
    setBookingsLoading(true)
    setBookingsError(null)
    setBookings([])
    try {
      const data = await getShortageBookings(alert.date, alert.roomTypeCode)
      setBookings(data || [])
    } catch (err) {
      setBookingsError(
        err instanceof Error
          ? err.message
          : 'Không thể tải danh sách booking liên quan.',
      )
    } finally {
      setBookingsLoading(false)
    }
  }, [])

  // S3-08: Tải lại danh sách booking
  const retryBookings = useCallback(async () => {
    if (!selectedAlert) return
    setBookingsLoading(true)
    setBookingsError(null)
    try {
      const data = await getShortageBookings(selectedAlert.date, selectedAlert.roomTypeCode)
      setBookings(data || [])
    } catch (err) {
      setBookingsError(
        err instanceof Error
          ? err.message
          : 'Không thể tải danh sách booking liên quan.',
      )
    } finally {
      setBookingsLoading(false)
    }
  }, [selectedAlert])

  // S3-08: Đóng danh sách booking quay lại khu vực cảnh báo
  const closeBookingList = useCallback(() => {
    setSelectedAlert(null)
    setBookings([])
    setBookingsError(null)
  }, [])

  return (
    <section
      className="shortage-alert-section"
      aria-label="Cảnh báo thiếu phòng"
      id="shortage-alert-section"
    >
      <div className="shortage-alert-header">
        <div className="shortage-alert-title-wrap">
          <span className="shortage-alert-icon" aria-hidden="true">
            ⚠️
          </span>
          <h3 className="shortage-alert-title">Cảnh báo thiếu phòng</h3>
          {!loading && !error && alerts.length > 0 && !selectedAlert && (
            <span className="shortage-count-badge" id="shortage-count-badge">
              {alerts.length} ngày bị thiếu
            </span>
          )}
        </div>

        {!selectedAlert && (
          <button
            type="button"
            className="shortage-refresh-btn"
            id="shortage-refresh-btn"
            onClick={() => void fetchAlerts()}
            disabled={loading}
            title="Làm mới cảnh báo"
          >
            {loading ? 'Đang tải...' : '↻ Làm mới'}
          </button>
        )}
      </div>

      {/* === KHU VỰC CẢNH BÁO (ẩn khi đang xem danh sách booking) === */}
      {!selectedAlert && (
        <>
          {loading && (
            <div className="shortage-loading" id="shortage-loading" role="status">
              <span className="shortage-spinner" />
              <span>Đang kiểm tra tình trạng phòng...</span>
            </div>
          )}

          {error && !loading && (
            <div className="shortage-error-box" id="shortage-error-box" role="alert">
              <div className="shortage-error-msg">
                <span>❌</span>
                <span>{error}</span>
              </div>
              <button
                type="button"
                className="shortage-retry-btn"
                id="shortage-retry-btn"
                onClick={() => void fetchAlerts()}
              >
                Tải lại
              </button>
            </div>
          )}

          {!loading && !error && alerts.length === 0 && (
            <div
              className="shortage-empty-box"
              id="shortage-empty-box"
              role="status"
            >
              <span className="shortage-empty-icon">✓</span>
              <p className="shortage-empty-text">Không có cảnh báo</p>
              <small className="shortage-empty-sub">
                Tất cả các loại phòng trong 30 ngày tới đều đủ phòng khả dụng cho các booking còn hiệu lực.
              </small>
            </div>
          )}

          {!loading && !error && alerts.length > 0 && (
            <div className="shortage-table-wrapper" id="shortage-table-wrapper">
              <table className="shortage-table" id="shortage-table">
                <thead>
                  <tr>
                    <th scope="col">Ngày</th>
                    <th scope="col">Tên loại phòng</th>
                    <th scope="col" className="text-center">Số booking</th>
                    <th scope="col" className="text-center">Số phòng khả dụng</th>
                    <th scope="col" className="text-center">Thiếu hụt</th>
                  </tr>
                </thead>
                <tbody>
                  {alerts.map((item, idx) => {
                    const deficit = item.bookingCount - item.availableRooms
                    const rowKey = `${item.date}_${item.roomTypeCode}_${idx}`
                    return (
                      <tr
                        key={rowKey}
                        className="shortage-row shortage-row-clickable"
                        id={`shortage-row-${idx}`}
                        onClick={() => void handleAlertClick(item)}
                        role="button"
                        tabIndex={0}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault()
                            void handleAlertClick(item)
                          }
                        }}
                        title="Bấm để xem danh sách booking liên quan"
                      >
                        <td className="shortage-cell-date">
                          <strong>{formatDate(item.date)}</strong>
                        </td>
                        <td className="shortage-cell-type">
                          <span className="shortage-room-name">{item.roomTypeName}</span>
                          <small className="shortage-room-code">({item.roomTypeCode})</small>
                        </td>
                        <td className="shortage-cell-bookings text-center">
                          <span className="badge-booking-count" id={`booking-count-${idx}`}>
                            {item.bookingCount} booking
                          </span>
                        </td>
                        <td className="shortage-cell-available text-center">
                          <span className="badge-available-count" id={`available-count-${idx}`}>
                            {item.availableRooms} phòng khả dụng
                          </span>
                        </td>
                        <td className="shortage-cell-deficit text-center">
                          <span className="badge-deficit" id={`deficit-${idx}`}>
                            Thiếu {deficit} phòng
                          </span>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}

      {/* === DANH SÁCH BOOKING LIÊN QUAN (hiện khi bấm vào cảnh báo) === */}
      {selectedAlert && (
        <div className="shortage-booking-panel" id="shortage-booking-panel">
          {/* Tiêu đề danh sách gồm ngày, loại phòng, số booking và số phòng khả dụng */}
          <div className="shortage-booking-panel-header">
            <div className="shortage-booking-panel-title">
              <h4>
                📋 Booking ngày{' '}
                <strong>{formatDate(selectedAlert.date)}</strong>
                {' — '}
                <span className="shortage-booking-room-type">{selectedAlert.roomTypeName}</span>
              </h4>
              <div className="shortage-booking-panel-badges">
                <span className="badge-booking-count">
                  {selectedAlert.bookingCount} booking
                </span>
                <span className="badge-available-count">
                  {selectedAlert.availableRooms} phòng khả dụng
                </span>
              </div>
            </div>
            <button
              type="button"
              className="shortage-close-btn"
              id="shortage-close-btn"
              onClick={closeBookingList}
              title="Đóng danh sách booking"
            >
              ✕ Đóng
            </button>
          </div>

          {/* Loading */}
          {bookingsLoading && (
            <div className="shortage-loading" id="shortage-bookings-loading" role="status">
              <span className="shortage-spinner" />
              <span>Đang tải danh sách booking...</span>
            </div>
          )}

          {/* Lỗi + nút tải lại */}
          {bookingsError && !bookingsLoading && (
            <div className="shortage-error-box" id="shortage-bookings-error" role="alert">
              <div className="shortage-error-msg">
                <span>❌</span>
                <span>{bookingsError}</span>
              </div>
              <button
                type="button"
                className="shortage-retry-btn"
                id="shortage-bookings-retry-btn"
                onClick={() => void retryBookings()}
              >
                Tải lại
              </button>
            </div>
          )}

          {/* Danh sách booking */}
          {!bookingsLoading && !bookingsError && bookings.length === 0 && (
            <div className="shortage-empty-box" id="shortage-bookings-empty" role="status">
              <span className="shortage-empty-icon">✓</span>
              <p className="shortage-empty-text">Không tìm thấy booking</p>
              <small className="shortage-empty-sub">
                Không có booking còn hiệu lực chiếm loại phòng này trong ngày đã chọn.
              </small>
            </div>
          )}

          {!bookingsLoading && !bookingsError && bookings.length > 0 && (
            <div className="shortage-table-wrapper" id="shortage-bookings-table-wrapper">
              <table className="shortage-table shortage-booking-table" id="shortage-bookings-table">
                <thead>
                  <tr>
                    <th scope="col">Mã booking</th>
                    <th scope="col">Tên khách</th>
                    <th scope="col">Số điện thoại</th>
                    <th scope="col">Ngày nhận</th>
                    <th scope="col">Ngày trả</th>
                    <th scope="col" className="text-center">Trạng thái</th>
                    <th scope="col" className="text-center">Kênh đặt</th>
                    <th scope="col">Thời điểm tạo</th>
                  </tr>
                </thead>
                <tbody>
                  {bookings.map((b, idx) => {
                    const statusLabel = STATUS_LABELS[b.status] ?? b.status
                    const statusClass = STATUS_CLASS_NAMES[b.status] ?? ''
                    const sourceLabel = b.source ? (SOURCE_LABELS[b.source] ?? b.source) : '—'
                    return (
                      <tr
                        key={b.id}
                        className="shortage-row shortage-booking-row"
                        id={`shortage-booking-row-${idx}`}
                      >
                        <td>
                          <span
                            className="shortage-booking-code-link"
                            id={`shortage-booking-link-${idx}`}
                            title="Xem chi tiết booking"
                          >
                            {b.bookingCode}
                          </span>
                        </td>
                        <td className="shortage-booking-guest">{b.guestName}</td>
                        <td>{b.guestPhone || '—'}</td>
                        <td>{formatDate(b.checkInDate)}</td>
                        <td>{formatDate(b.checkOutDate)}</td>
                        <td className="text-center">
                          <span className={`shortage-booking-status shortage-booking-status--${statusClass}`}>
                            {statusLabel}
                            {b.holdExpired && (
                              <small className="shortage-booking-expired"> (hết hạn)</small>
                            )}
                          </span>
                        </td>
                        <td className="text-center">
                          <span className="shortage-booking-source">{sourceLabel}</span>
                        </td>
                        <td className="shortage-booking-created">
                          {formatDateTime(b.createdAt)}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </div>
      )}
    </section>
  )
}
