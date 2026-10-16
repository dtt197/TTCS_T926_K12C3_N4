import { useEffect, useState } from 'react'
import { getRevenueReport } from '../services/revenueReportService'
import type { RevenueReport } from '../types/revenueReport'
import './RevenueReportPage.css'

/** Cùng giới hạn với backend (S4-07 Lát 1). */
const MAX_RANGE_DAYS = 366

type DateRange = {
  startDate: string
  endDate: string
}

type AppliedRange = DateRange & { id: number }

type LoadState = {
  id: number
  report?: RevenueReport
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
  const [applied, setApplied] = useState<AppliedRange>({ ...initialRange, id: 1 })
  const [formError, setFormError] = useState('')
  const [state, setState] = useState<LoadState>({ id: 0 })

  useEffect(() => {
    let cancelled = false

    getRevenueReport(applied.startDate, applied.endDate)
      .then((report) => {
        if (!cancelled) setState({ id: applied.id, report })
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
  const loadError = loading ? '' : (state.error ?? '')

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const message = validateRange(form)

    if (message) {
      setFormError(message)
      return
    }

    setFormError('')
    setApplied((current) => ({ ...form, id: current.id + 1 }))
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

        <button type="submit" className="revenue-primary" disabled={loading}>
          Xem thống kê
        </button>
      </form>

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