import { useState } from 'react'
import { ApiRequestError } from '../services/apiClient'
import { getOccupancyReport, type OccupancyReport } from '../services/occupancyReportService'
import './OccupancyReportPage.css'

function today() {
  return new Date().toISOString().slice(0, 10)
}

function formatPercent(value: number | null) {
  return value == null ? 'Không có phòng khả dụng' : `${value.toFixed(2)}%`
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN').format(new Date(`${value}T00:00:00`))
}

export function OccupancyReportPage() {
  const [startDate, setStartDate] = useState(today)
  const [endDate, setEndDate] = useState(today)
  const [report, setReport] = useState<OccupancyReport | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function viewReport() {
    setError(null)
    setReport(null)
    if (!startDate || !endDate) {
      setError('Vui lòng chọn ngày bắt đầu và ngày kết thúc.')
      return
    }
    if (endDate < startDate) {
      setError('Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.')
      return
    }
    const days = Math.floor((Date.parse(`${endDate}T00:00:00`) - Date.parse(`${startDate}T00:00:00`)) / 86_400_000) + 1
    if (days > 366) {
      setError('Khoảng ngày báo cáo không được vượt quá 366 ngày.')
      return
    }
    setLoading(true)
    try {
      setReport(await getOccupancyReport(startDate, endDate))
    } catch (reason) {
      setError(reason instanceof ApiRequestError ? reason.message : 'Không tải được báo cáo công suất.')
    } finally {
      setLoading(false)
    }
  }

  return <section className="occupancy-report" aria-label="Báo cáo công suất phòng">
    <form className="occupancy-report-filter" onSubmit={event => { event.preventDefault(); void viewReport() }}>
      <label>Ngày bắt đầu<input type="date" value={startDate} onChange={event => setStartDate(event.target.value)} /></label>
      <label>Ngày kết thúc<input type="date" value={endDate} onChange={event => setEndDate(event.target.value)} /></label>
      <button type="submit" disabled={loading}>{loading ? 'Đang xem…' : 'Xem báo cáo'}</button>
    </form>
    <p className="occupancy-report-note">Đêm tính theo ngày nhận phòng. Booking đã xác nhận, đã nhận phòng và đã trả phòng được tính; phòng bảo trì được loại khỏi số đêm khả dụng.</p>
    {error && <p className="occupancy-report-error" role="alert">{error}</p>}
    {report && <>
      <article className="occupancy-average">
        <span>Công suất trung bình kỳ</span>
        <strong>{formatPercent(report.averageOccupancyPercent)}</strong>
        <small>{report.totalSoldNights} đêm đã bán / {report.totalAvailableNights} đêm khả dụng</small>
      </article>
      <div className="occupancy-table-wrap">
        <table>
          <caption>Công suất từng ngày từ {formatDate(report.startDate)} đến {formatDate(report.endDate)}</caption>
          <thead><tr><th>Ngày</th><th>Đêm đã bán</th><th>Đêm khả dụng</th><th>Công suất</th></tr></thead>
          <tbody>{report.dailyOccupancies.map(day => <tr key={day.date}>
            <td>{formatDate(day.date)}</td><td>{day.soldNights}</td><td>{day.availableNights}</td><td>{formatPercent(day.occupancyPercent)}</td>
          </tr>)}</tbody>
        </table>
      </div>
    </>}
  </section>
}
