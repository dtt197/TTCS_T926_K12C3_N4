import { useState } from 'react'
import { downloadOccupancyCsv, getOccupancyReport, type OccupancyByRoomTypeReport } from '../services/occupancyService'
import { getDetailedOccupancyReport, type DetailedOccupancyReport } from '../services/occupancyReportService'
import './OccupancyReportPage.css'

const today = new Date().toISOString().slice(0, 10)
const formatPercent = (value: number | null) => value == null
  ? 'Không có phòng khả dụng'
  : `${value.toLocaleString('vi-VN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`
const formatDate = (value: string) => new Intl.DateTimeFormat('vi-VN').format(new Date(`${value}T00:00:00`))

export function OccupancyReportPage() {
  const [startDate, setStartDate] = useState(today)
  const [endDate, setEndDate] = useState(today)
  const [roomTypeId, setRoomTypeId] = useState('')
  const [report, setReport] = useState<OccupancyByRoomTypeReport | null>(null)
  const [detail, setDetail] = useState<DetailedOccupancyReport | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  function validate() {
    if (!startDate || !endDate) return 'Vui lòng chọn ngày bắt đầu và ngày kết thúc.'
    if (endDate < startDate) return 'Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.'
    const days = (Date.parse(`${endDate}T00:00:00`) - Date.parse(`${startDate}T00:00:00`)) / 86_400_000 + 1
    return days > 366 ? 'Khoảng ngày báo cáo không được vượt quá 366 ngày.' : ''
  }

  async function load(typeId = roomTypeId) {
    const validation = validate()
    if (validation) { setError(validation); return }
    setLoading(true)
    setError('')
    try {
      const [summary, detailed] = await Promise.all([
        getOccupancyReport(startDate, endDate, typeId),
        getDetailedOccupancyReport(startDate, endDate, typeId),
      ])
      setReport(summary)
      setDetail(detailed)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Không thể tải báo cáo công suất.')
    } finally {
      setLoading(false)
    }
  }

  function changeType(value: string) {
    setRoomTypeId(value)
    if (report) void load(value)
  }

  async function download() {
    const validation = validate()
    if (validation) { setError(validation); return }
    try {
      setError('')
      await downloadOccupancyCsv(startDate, endDate, roomTypeId)
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Không thể tải tệp CSV.')
    }
  }

  const selected = report?.roomTypes.find(type => type.roomTypeId === Number(roomTypeId))

  return <section className="occupancy-report" aria-label="Báo cáo công suất phòng">
    <form className="occupancy-filter" onSubmit={event => { event.preventDefault(); void load() }}>
      <label>Từ ngày<input type="date" value={startDate} onChange={event => setStartDate(event.target.value)} /></label>
      <label>Đến ngày<input type="date" value={endDate} onChange={event => setEndDate(event.target.value)} /></label>
      <button type="submit" disabled={loading}>{loading ? 'Đang tải…' : 'Xem báo cáo'}</button>
      <button type="button" className="csv-button" onClick={() => void download()} disabled={loading}>Tải CSV</button>
    </form>
    {report && <label className="occupancy-type">Loại phòng
      <select value={roomTypeId} onChange={event => changeType(event.target.value)} disabled={loading}>
        <option value="">Tất cả loại phòng</option>
        {report.roomTypes.map(type => <option key={type.roomTypeId} value={type.roomTypeId}>{type.code} — {type.name}{type.active ? '' : ' (ngừng bán)'}</option>)}
      </select>
    </label>}
    {error && <p className="occupancy-error" role="alert">{error}</p>}
    {report && <>
      <div className="occupancy-summary">
        <article><span>Công suất toàn bộ</span><strong>{formatPercent(report.totalAverageOccupancyPercent)}</strong><small>{report.totalSoldNights} / {report.totalAvailableNights} đêm</small></article>
        {selected && <article><span>{selected.name}</span><strong>{formatPercent(selected.averageOccupancyPercent)}</strong><small>{selected.soldNights} / {selected.availableNights} đêm</small></article>}
      </div>
      <div className="occupancy-types"><h2>Theo loại phòng</h2>{report.roomTypes.map(type => <article key={type.roomTypeId}><span>{type.code} · {type.name}</span><strong>{formatPercent(type.averageOccupancyPercent)}</strong><small>{type.soldNights} đêm bán / {type.availableNights} đêm khả dụng</small></article>)}</div>
    </>}
    {detail && <div className="occupancy-table-wrap"><table>
      <caption>Công suất từng ngày {roomTypeId ? `— ${detail.selectedRoomTypeName}` : `từ ${formatDate(detail.startDate)} đến ${formatDate(detail.endDate)}`}</caption>
      <thead><tr><th>Ngày</th><th>Đêm đã bán</th><th>Đêm khả dụng</th><th>Công suất</th></tr></thead>
      <tbody>{detail.dailyOccupancies.map(day => <tr key={day.date}>
        <td>{formatDate(day.date)}</td><td>{day.soldNights}</td><td>{day.availableNights}</td><td>{formatPercent(day.occupancyPercent)}</td>
      </tr>)}</tbody>
    </table></div>}
  </section>
}
