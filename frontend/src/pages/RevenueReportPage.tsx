import { useEffect, useState } from 'react'
import {
  getRevenueReport,
  getRevenueByRoomType,
  getRevenueByDay,
  getRevenueComparison,
  getRevenueByRoomTypeComparison,
} from '../services/revenueReportService'
import { getRoomTypes } from '../services/roomTypeService'
import type {
  RevenueReport,
  RevenueByRoomType,
  RevenueByDay,
  RevenueComparison,
  RevenueByRoomTypeComparison,
} from '../types/revenueReport'
import type { RoomType } from '../types/roomType'
import './RevenueReportPage.css'

/** Cùng giới hạn với backend (S4-07 Lát 1). */
const MAX_RANGE_DAYS = 366

type DateRange = {
  startDate: string
  endDate: string
  roomTypeId: string
}

type AppliedRange = DateRange & {
  id: number
  roomTypeId: string
}

type LoadState = {
  id: number
  report?: RevenueReport
  roomTypeRows?: RevenueByRoomType[]
  dailyRows?: RevenueByDay[]
  comparison?: RevenueComparison
  roomTypeComparisonRows?: RevenueByRoomTypeComparison[]
  error?: string
}

function toIso(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

/** Mặc định: từ đầu tháng này đến hôm nay. */
function defaultRange(role: string): DateRange {
  const now = new Date()
  const today = toIso(now)

  if (role === 'RECEPTIONIST') {
    return {
      startDate: today,
      endDate: today,
      roomTypeId: '',
    }
  }

  return {
    startDate: toIso(
      new Date(now.getFullYear(), now.getMonth(), 1),
    ),
    endDate: today,
    roomTypeId: '',
  }
}

function validateRange({ startDate, endDate }: DateRange): string {
  if (!startDate || !endDate) {
    return 'Vui lòng chọn ngày bắt đầu và ngày kết thúc.'
  }

  if (endDate < startDate) {
    return 'Ngày kết thúc phải sau hoặc bằng ngày bắt đầu.'
  }

  const days = (Date.parse(endDate) - Date.parse(startDate)) / 86_400_000 + 1

  if (days > MAX_RANGE_DAYS) {
    return `Khoảng ngày thống kê tối đa ${MAX_RANGE_DAYS} ngày.`
  }

  return ''
}

function formatMoney(value: number) {
  return `${value.toLocaleString('vi-VN')} ₫`
}

function formatDate(isoDate: string) {
  const [year, month, day] = isoDate.split('-')

  return `${day}/${month}/${year}`
}

/** S4-07 Lát 1: chủ homestay chọn khoảng ngày và xem doanh thu của các booking đã đóng. */
export function RevenueReportPage({
  role,
}: {
  role: string
}) {
  const [initialRange] = useState(() => defaultRange(role))
  const [form, setForm] = useState<DateRange>(initialRange)
  const [applied, setApplied] = useState<AppliedRange>({
    ...initialRange,
    roomTypeId: '',
    id: 1,
  })
  const [formError, setFormError] = useState('')
  const [state, setState] = useState<LoadState>({ id: 0 })
  const [roomTypes, setRoomTypes] = useState<RoomType[]>([])
  const [roomTypesError, setRoomTypesError] = useState('')

  useEffect(() => {
    let cancelled = false

    getRoomTypes()
      .then((items) => {
        if (!cancelled) {
          setRoomTypes(items)
          setRoomTypesError('')
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setRoomTypesError(
            err instanceof Error
              ? err.message
              : 'Không tải được danh sách loại phòng.',
          )
        }
      })

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    let cancelled = false

    const roomTypeId = applied.roomTypeId
      ? Number(applied.roomTypeId)
      : undefined

    const canCompare =
      role === 'OWNER' || role === 'ADMIN'

    Promise.all([
      getRevenueReport(
        applied.startDate,
        applied.endDate,
      ),

      getRevenueByRoomType(
        applied.startDate,
        applied.endDate,
        roomTypeId,
      ),

      getRevenueByDay(
        applied.startDate,
        applied.endDate,
        roomTypeId,
      ),

      canCompare
        ? getRevenueComparison(
          applied.startDate,
          applied.endDate,
        )
        : Promise.resolve(undefined),

      canCompare
        ? getRevenueByRoomTypeComparison(
          applied.startDate,
          applied.endDate,
          roomTypeId,
        )
        : Promise.resolve(undefined),
    ])
      .then((
        [
          report,
          roomTypeRows,
          dailyRows,
          comparison,
          roomTypeComparisonRows,
        ],
      ) => {
        if (!cancelled) {
          setState({
            id: applied.id,
            report,
            roomTypeRows,
            dailyRows,
            comparison,
            roomTypeComparisonRows,
          })
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setState({
            id: applied.id,
            error:
              err instanceof Error
                ? err.message
                : 'Không tải được thống kê doanh thu.',
          })
        }
      })

    return () => {
      cancelled = true
    }
  }, [applied, role])

  const loading = state.id !== applied.id
  const report = loading ? undefined : state.report
  const roomTypeRows = loading ? undefined : state.roomTypeRows
  const dailyRows = loading ? undefined : state.dailyRows
  const comparison = loading ? undefined : state.comparison

  const roomTypeComparisonRows = loading
    ? undefined
    : state.roomTypeComparisonRows
  const loadError = loading ? '' : (state.error ?? '')
  const roomRevenueShare = report && report.totalRevenue > 0
    ? (report.roomRevenue / report.totalRevenue) * 100
    : 0

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const message = validateRange(form)

    if (message) {
      setFormError(message)
      return
    }

    setFormError('')

    const today = toIso(new Date())

    setApplied((current) => ({
      ...form,
      startDate:
        role === 'RECEPTIONIST'
          ? today
          : form.startDate,
      endDate:
        role === 'RECEPTIONIST'
          ? today
          : form.endDate,
      roomTypeId: form.roomTypeId,
      id: current.id + 1,
    }))
  }

  return (
    <section className="revenue-page">
      <div className="revenue-topline">
        <div className="revenue-header">
          <span className="revenue-title-icon" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round"><path d="M4 19V5m0 14h16M8 15v-4m4 4V7m4 8v-6m4 6V9" /></svg>
          </span>
          <div>
            <h2>Doanh thu</h2>
            <p>Chỉ tính các booking đã đóng (đã trả phòng). Booking thuộc khoảng ngày theo ngày trả phòng.</p>
          </div>
        </div>
      </div>
      <form className="revenue-filter" onSubmit={handleSubmit} noValidate>
        <label>
          <span>Từ ngày</span>

          <input
            type="date"
            disabled={role === 'RECEPTIONIST'}
            value={form.startDate}
            onChange={(event) => {
              setForm((current) => ({ ...current, startDate: event.target.value }))
              setFormError('')
            }}
          />
        </label>

        <label>
          <span>Đến ngày</span>

          <input
            type="date"
            disabled={role === 'RECEPTIONIST'}
            value={form.endDate}
            onChange={(event) => {
              setForm((current) => ({ ...current, endDate: event.target.value }))
              setFormError('')
            }}
          />
        </label>

        <label>
          <span>Loại phòng</span>

          <select
            value={form.roomTypeId}
            onChange={(event) => {
              setForm((current) => ({
                ...current,
                roomTypeId: event.target.value,
              }))
              setFormError('')
            }}
          >
            <option value="">Tất cả loại phòng</option>

            {roomTypes.map((roomType) => (
              <option key={roomType.id} value={String(roomType.id)}>
                {roomType.code} - {roomType.name}
              </option>
            ))}
          </select>
        </label>

        <button type="submit" className="revenue-primary" disabled={loading}>
          Xem thống kê
        </button>
      </form>
      
      {roomTypesError && (
        <div className="revenue-message error" role="alert">
          {roomTypesError}
        </div>
      )}

      {formError && (
        <div className="revenue-message error" role="alert">
          {formError}
        </div>
      )}

      {loadError && (
        <div className="revenue-message error" role="alert">
          {loadError}
        </div>
      )}

      {loading && <p className="revenue-loading">Đang tải thống kê...</p>}

      {report && (
        <>
          <div className="revenue-period-row">
          <p className="revenue-period">
            Từ {formatDate(report.startDate)} đến {formatDate(report.endDate)}
          </p>
          </div>

          <div className="revenue-cards">
            <article className="revenue-card revenue-card--total">
              <span className="revenue-card-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M4 7h16v12H4zM4 7l2-3h12l2 3M8 12h8m-8 3h5" /></svg></span>
              <div className="revenue-card-content"><span>Tổng doanh thu</span><strong>{formatMoney(report.totalRevenue)}</strong></div>
            </article>
            <article className="revenue-card revenue-card--bookings">
              <span className="revenue-card-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><rect x="4" y="5" width="16" height="16" rx="2"/><path d="M8 3v4m8-4v4M4 10h16m-9 4 2 2 4-4" /></svg></span>
              <div className="revenue-card-content"><span>Số booking đã đóng</span><strong>{report.closedBookingCount}</strong></div>
            </article>
            <article className="revenue-card revenue-card--rate">
              <span className="revenue-card-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2m-8-7 1 1" /></svg></span>
              <div className="revenue-card-content"><span>Giá bán trung bình mỗi đêm</span><strong>
                {report.averageNightlyRate === null
                  ? '—'
                  : formatMoney(report.averageNightlyRate)}
              </strong></div>
            </article>
            <article className="revenue-card revenue-card--rooms">
              <span className="revenue-card-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M3 20V8l9-5 9 5v12M7 20v-7h10v7M8 9h.01M12 9h.01M16 9h.01" /></svg></span>
              <div className="revenue-card-content"><span>Tiền phòng</span><strong>{formatMoney(report.roomRevenue)}</strong></div>
            </article>
            <article className="revenue-card revenue-card--surcharge">
              <span className="revenue-card-icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M12 3v18m5-14H9a3 3 0 0 0 0 6h6a3 3 0 0 1 0 6H6" /></svg></span>
              <div className="revenue-card-content"><span>Tiền phụ thu</span><strong>{formatMoney(report.surchargeRevenue)}</strong></div>
            </article>
          </div>

          <div className="revenue-visual-grid">
            <section className="revenue-chart-panel" aria-labelledby="revenue-chart-title">
              <div className="revenue-section-heading"><div><span className="revenue-eyebrow">XU HƯỚNG</span><h3 id="revenue-chart-title">Doanh thu theo ngày</h3></div><span className="revenue-chart-caption">Theo ngày trả phòng</span></div>
              {dailyRows && dailyRows.length > 0 ? (
                <div className="revenue-daily-chart" role="img" aria-label="Biểu đồ doanh thu theo ngày trả phòng">
                  <div className="revenue-daily-y-axis"><span>{formatMoney(Math.max(0, ...dailyRows.map((item) => item.totalRevenue)))}</span><span>{formatMoney(Math.max(0, ...dailyRows.map((item) => item.totalRevenue)) / 2)}</span><span>0 ₫</span></div>
                  <div className="revenue-daily-bars">
                    {dailyRows.map((item) => {
                      const maxRevenue = Math.max(1, ...dailyRows.map((row) => row.totalRevenue))
                      const height = (item.totalRevenue / maxRevenue) * 100
                      return (
                        <div className="revenue-daily-point" key={item.date} title={`${formatDate(item.date)}: ${formatMoney(item.totalRevenue)}`}>
                          <div className="revenue-daily-bar-track"><div className="revenue-daily-bar" style={{ height: `${height}%` }} /></div>
                          <span className="revenue-daily-date">{item.date.slice(8, 10)}/{item.date.slice(5, 7)}</span>
                        </div>
                      )
                    })}
                  </div>
                </div>
              ) : <div className="revenue-daily-empty">Không có dữ liệu doanh thu trong khoảng ngày đã chọn.</div>}
            </section>
            <section className="revenue-chart-panel" aria-labelledby="revenue-mix-title">
              <div className="revenue-section-heading"><div><span className="revenue-eyebrow">CƠ CẤU DOANH THU</span><h3 id="revenue-mix-title">Tiền phòng và phụ thu</h3></div></div>
              <div className="revenue-donut-layout">
                <div className={`revenue-donut${report.totalRevenue <= 0 ? ' revenue-donut--empty' : ''}`} role="img" aria-label={`Tiền phòng ${formatMoney(report.roomRevenue)}, phụ thu ${formatMoney(report.surchargeRevenue)}`} style={{ '--room-share': `${roomRevenueShare}%` } as React.CSSProperties}>
                  <div className="revenue-donut-hole"><strong>{formatMoney(report.totalRevenue)}</strong><span>Tổng doanh thu</span></div>
                </div>
                <div className="revenue-donut-legend">
                  <div><i className="revenue-donut-dot"/><span>Tiền phòng</span><strong>{formatMoney(report.roomRevenue)}</strong></div>
                  <div><i className="revenue-donut-dot revenue-donut-dot--surcharge"/><span>Phụ thu</span><strong>{formatMoney(report.surchargeRevenue)}</strong></div>
                </div>
              </div>
            </section>
          </div>

          {role !== 'RECEPTIONIST' && comparison && (
            <section className="revenue-comparison">
              <h3>So sánh doanh thu với kỳ liền trước</h3>

              <div className="revenue-cards">
                <article className="revenue-card">
                  <span>Doanh thu kỳ hiện tại</span>
                  <strong>
                    {formatMoney(comparison.currentRevenue)}
                  </strong>
                </article>

                <article className="revenue-card">
                  <span>Doanh thu kỳ trước</span>
                  <strong>
                    {formatMoney(comparison.previousRevenue)}
                  </strong>
                </article>

                <article
                  className={`revenue-card revenue-comparison--${comparison.changeDirection.toLowerCase()}`}
                >
                  <span>Chênh lệch doanh thu</span>
                  <strong>
                    {comparison.differenceAmount > 0 ? '+' : ''}
                    {formatMoney(comparison.differenceAmount)}
                  </strong>
                </article>

                <article
                  className={`revenue-card revenue-comparison--${comparison.changeDirection.toLowerCase()}`}
                >
                  <span>Tỷ lệ thay đổi</span>
                  <strong>
                    {comparison.changePercent === null
                      ? 'Không xác định'
                      : `${comparison.changePercent}%`}
                  </strong>
                </article>
              </div>

              <p className="revenue-comparison-message">
                {comparison.message}
              </p>
            </section>
          )}
          
          <div className="revenue-report-grid">
            {roomTypeRows && <section className="revenue-room-types">
              <h3>Doanh thu theo loại phòng</h3>
              <p className="revenue-room-types-description">Các số liệu được tính từ booking đã trả phòng trong khoảng ngày đã chọn.</p>
              <div className="revenue-table-wrapper"><table className="revenue-table"><thead><tr><th>Mã phòng</th><th>Loại phòng</th><th>Booking đã đóng</th><th>Số đêm</th><th>Tiền phòng</th><th>Phụ thu</th><th>Tổng doanh thu</th><th>Trung bình/đêm</th></tr></thead><tbody>
                {roomTypeRows.map((item) => <tr key={item.roomTypeId}><td>{item.roomTypeCode}</td><td>{item.roomTypeName}</td><td>{item.closedBookingCount}</td><td>{item.totalNights}</td><td>{formatMoney(item.roomRevenue)}</td><td>{formatMoney(item.surchargeRevenue)}</td><td className="revenue-table-total">{formatMoney(item.totalRevenue)}</td><td>{item.averageNightlyRate === null ? '—' : formatMoney(item.averageNightlyRate)}</td></tr>)}
                {roomTypeRows.length === 0 && <tr><td colSpan={8} className="revenue-table-empty">Không tìm thấy loại phòng phù hợp.</td></tr>}
              </tbody></table></div>
              {applied.roomTypeId === '' && <div className="revenue-reconciliation"><span>Tổng doanh thu theo loại phòng</span><strong>{formatMoney(roomTypeRows.reduce((sum, item) => sum + item.totalRevenue, 0))}</strong><span>Tổng doanh thu toàn homestay</span><strong>{formatMoney(report.totalRevenue)}</strong><p>{roomTypeRows.reduce((sum, item) => sum + item.totalRevenue, 0) === report.totalRevenue ? 'Đối chiếu thành công: hai tổng doanh thu khớp nhau.' : 'Cảnh báo: tổng doanh thu theo loại phòng chưa khớp tổng doanh thu toàn homestay.'}</p></div>}
            </section>}
            {role !== 'RECEPTIONIST' && roomTypeComparisonRows && (
            <section className="revenue-room-types revenue-room-types--comparison">
              <h3>So sánh doanh thu theo loại phòng</h3>

              <div className="revenue-table-wrapper">
                <table className="revenue-table">
                  <thead>
                    <tr>
                      <th>Mã phòng</th>
                      <th>Loại phòng</th>
                      <th>Doanh thu kỳ này</th>
                      <th>Doanh thu kỳ trước</th>
                      <th>Chênh lệch</th>
                      <th>Tỷ lệ thay đổi</th>
                      <th>Trạng thái</th>
                    </tr>
                  </thead>

                  <tbody>
                    {roomTypeComparisonRows.map((item) => (
                      <tr key={item.roomTypeId}>
                        <td>{item.roomTypeCode}</td>
                        <td>{item.roomTypeName}</td>

                        <td>{formatMoney(item.currentRevenue)}</td>

                        <td>{formatMoney(item.previousRevenue)}</td>

                        <td
                          className={`revenue-comparison--${item.changeDirection.toLowerCase()}`}
                        >
                          {item.differenceAmount > 0 ? '+' : ''}
                          {formatMoney(item.differenceAmount)}
                        </td>

                        <td>
                          {item.changePercent === null
                            ? 'Không xác định'
                            : `${item.changePercent}%`}
                        </td>

                        <td>{item.message}</td>
                      </tr>
                    ))}

                    {roomTypeComparisonRows.length === 0 && (
                      <tr>
                        <td colSpan={7} className="revenue-table-empty">
                          Không tìm thấy loại phòng phù hợp.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </section>
          )}
          </div>

          {report.closedBookingCount === 0 && (
            <p className="revenue-empty">
              Không có booking đã đóng trong khoảng ngày này.
            </p>
          )}
        </>
      )}
      <footer>HomeStay Manager · Báo cáo doanh thu</footer>
    </section>
  )
}
