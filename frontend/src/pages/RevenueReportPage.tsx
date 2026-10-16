import { useEffect, useState } from 'react'
import { getRevenueReport } from '../services/revenueReportService'
import { getRevenueByRoomType } from '../services/revenueReportService'
import { getRoomTypes } from '../services/roomTypeService'
import type { RevenueReport } from '../types/revenueReport'
import type { RevenueByRoomType } from '../types/revenueReport'
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
  error?: string
}

function toIso(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

/** Mặc định: từ đầu tháng này đến hôm nay. */
function defaultRange(): DateRange {
  const now = new Date()

  return {
    startDate: toIso(new Date(now.getFullYear(), now.getMonth(), 1)),
    endDate: toIso(now),
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
export function RevenueReportPage() {
  const [initialRange] = useState(defaultRange)
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

    Promise.all([
      getRevenueReport(applied.startDate, applied.endDate),
      getRevenueByRoomType(
        applied.startDate,
        applied.endDate,
        roomTypeId,
      ),
    ])
      .then(([report, roomTypeRows]) => {
        if (!cancelled) {
          setState({
            id: applied.id,
            report,
            roomTypeRows,
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
  }, [applied])

  const loading = state.id !== applied.id
  const report = loading ? undefined : state.report
  const roomTypeRows = loading ? undefined : state.roomTypeRows
  const loadError = loading ? '' : (state.error ?? '')

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const message = validateRange(form)

    if (message) {
      setFormError(message)
      return
    }

    setFormError('')
    setApplied((current) => ({
      ...form,
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