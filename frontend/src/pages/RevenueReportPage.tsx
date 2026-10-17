import { useEffect, useState } from 'react'
import {
  getRevenueReport,
  getRevenueByRoomType,
  getRevenueComparison,
  getRevenueByRoomTypeComparison,
} from '../services/revenueReportService'
import { getRoomTypes } from '../services/roomTypeService'
import type {
  RevenueReport,
  RevenueByRoomType,
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
          comparison,
          roomTypeComparisonRows,
        ],
      ) => {
        if (!cancelled) {
          setState({
            id: applied.id,
            report,
            roomTypeRows,
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
  const comparison = loading ? undefined : state.comparison

  const roomTypeComparisonRows = loading
    ? undefined
    : state.roomTypeComparisonRows
  const loadError = loading ? '' : (state.error ?? '')

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
      <div className="revenue-header">
        <h2>Thống kê doanh thu</h2>

        <p>
          Chỉ tính các booking đã đóng (đã trả phòng). Booking thuộc khoảng
          ngày theo ngày trả phòng.
        </p>
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
          <p className="revenue-period">
            Từ {formatDate(report.startDate)} đến {formatDate(report.endDate)}
          </p>

          <div className="revenue-cards">
            <article className="revenue-card revenue-card--total">
              <span>Tổng doanh thu</span>
              <strong>{formatMoney(report.totalRevenue)}</strong>
            </article>

            <article className="revenue-card">
              <span>Số booking đã đóng</span>
              <strong>{report.closedBookingCount}</strong>
            </article>

            <article className="revenue-card">
              <span>Giá bán trung bình mỗi đêm</span>
              <strong>
                {report.averageNightlyRate === null
                  ? '—'
                  : formatMoney(report.averageNightlyRate)}
              </strong>
            </article>

            <article className="revenue-card">
              <span>Tiền phòng</span>
              <strong>{formatMoney(report.roomRevenue)}</strong>
            </article>

            <article className="revenue-card">
              <span>Tiền phụ thu</span>
              <strong>{formatMoney(report.surchargeRevenue)}</strong>
            </article>
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
          
          {role !== 'RECEPTIONIST' &&
            roomTypeComparisonRows && (
            <section className="revenue-room-types">
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

          {roomTypeRows && (
            <section className="revenue-room-types">
              <h3>Doanh thu theo loại phòng</h3>

              <p className="revenue-room-types-description">
                Các số liệu được tính từ booking đã trả phòng
                trong khoảng ngày đã chọn.
              </p>

              <div className="revenue-table-wrapper">
                <table className="revenue-table">
                  <thead>
                    <tr>
                      <th>Mã phòng</th>
                      <th>Loại phòng</th>
                      <th>Booking đã đóng</th>
                      <th>Số đêm</th>
                      <th>Tiền phòng</th>
                      <th>Phụ thu</th>
                      <th>Tổng doanh thu</th>
                      <th>Trung bình/đêm</th>
                    </tr>
                  </thead>

                  <tbody>
                    {roomTypeRows.map((item) => (
                      <tr key={item.roomTypeId}>
                        <td>{item.roomTypeCode}</td>
                        <td>{item.roomTypeName}</td>
                        <td>{item.closedBookingCount}</td>
                        <td>{item.totalNights}</td>
                        <td>{formatMoney(item.roomRevenue)}</td>
                        <td>{formatMoney(item.surchargeRevenue)}</td>
                        <td className="revenue-table-total">
                          {formatMoney(item.totalRevenue)}
                        </td>
                        <td>
                          {item.averageNightlyRate === null
                            ? '—'
                            : formatMoney(item.averageNightlyRate)}
                        </td>
                      </tr>
                    ))}

                    {roomTypeRows.length === 0 && (
                      <tr>
                        <td colSpan={8} className="revenue-table-empty">
                          Không tìm thấy loại phòng phù hợp.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>

              {applied.roomTypeId === '' && (
                <div className="revenue-reconciliation">
                  <span>Tổng doanh thu theo loại phòng</span>

                  <strong>
                    {formatMoney(
                      roomTypeRows.reduce(
                        (sum, item) => sum + item.totalRevenue,
                        0,
                      ),
                    )}
                  </strong>

                  <span>Tổng doanh thu toàn homestay</span>

                  <strong>{formatMoney(report.totalRevenue)}</strong>

                  <p>
                    {roomTypeRows.reduce(
                      (sum, item) => sum + item.totalRevenue,
                      0,
                    ) === report.totalRevenue
                      ? 'Đối chiếu thành công: hai tổng doanh thu khớp nhau.'
                      : 'Cảnh báo: tổng doanh thu theo loại phòng chưa khớp tổng doanh thu toàn homestay.'}
                  </p>
                </div>
              )}
            </section>
          )}

          {report.closedBookingCount === 0 && (
            <p className="revenue-empty">
              Không có booking đã đóng trong khoảng ngày này.
            </p>
          )}
        </>
      )}
    </section>
  )
}