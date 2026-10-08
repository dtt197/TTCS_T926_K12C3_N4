import { useEffect, useState, useCallback } from 'react'
import type { RoomShortageAlert } from '../types/roomShortage'
import { getRoomShortageAlerts } from '../services/roomShortageService'
import './RoomShortageAlertSection.css'

function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const parts = dateStr.split('-')
  if (parts.length === 3) {
    const [year, month, day] = parts
    return `${day}/${month}/${year}`
  }
  return dateStr
}

export function RoomShortageAlertSection() {
  const [alerts, setAlerts] = useState<RoomShortageAlert[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

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
          {!loading && !error && alerts.length > 0 && (
            <span className="shortage-count-badge" id="shortage-count-badge">
              {alerts.length} ngày bị thiếu
            </span>
          )}
        </div>

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
      </div>

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
                  <tr key={rowKey} className="shortage-row" id={`shortage-row-${idx}`}>
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
    </section>
  )
}

