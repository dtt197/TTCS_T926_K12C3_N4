import { useState } from 'react'
import { downloadOccupancyCsv, getOccupancyReport, type OccupancyReport } from '../services/occupancyService'
import './OccupancyReportPage.css'

const today = new Date().toISOString().slice(0, 10)
const percent = (value: number | null) => value === null ? 'Không có phòng khả dụng' : `${value.toLocaleString('vi-VN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}%`

import { ApiRequestError } from '../services/apiClient'
import { getOccupancyReport, type OccupancyReport } from '../services/occupancyService'
import './OccupancyReportPage.css'

const today = () => new Date().toISOString().slice(0, 10)
const percent = (value: number | null) => value == null ? 'Không có phòng khả dụng' : `${value.toFixed(2)}%`
const dateText = (value: string) => new Intl.DateTimeFormat('vi-VN').format(new Date(`${value}T00:00:00`))
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
  const [roomTypeId, setRoomTypeId] = useState('')
  const [report, setReport] = useState<OccupancyReport | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const validate = () => {
    if (!startDate || !endDate) return 'Vui lòng chọn ngày bắt đầu và ngày kết thúc.'
    if (endDate < startDate) return 'Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.'
    const days = (Date.parse(`${endDate}T00:00:00`) - Date.parse(`${startDate}T00:00:00`)) / 86_400_000 + 1
    return days > 366 ? 'Khoảng ngày báo cáo không được vượt quá 366 ngày.' : ''
  }
  const load = async (typeId = roomTypeId) => {
    const validation = validate(); if (validation) { setError(validation); return }
    setLoading(true); setError('')
    try { setReport(await getOccupancyReport(startDate, endDate, typeId)) }
    catch (e) { setError(e instanceof Error ? e.message : 'Không thể tải báo cáo.') }
    finally { setLoading(false) }
  }
  const changeType = (value: string) => { setRoomTypeId(value); if (report) void load(value) }
  const download = async () => {
    const validation = validate(); if (validation) { setError(validation); return }
    try { setError(''); await downloadOccupancyCsv(startDate, endDate, roomTypeId) }
    catch (e) { setError(e instanceof Error ? e.message : 'Không thể tải tệp CSV.') }
  }
  return <section className="occupancy-report">
    <div className="occupancy-filter"><label>Từ ngày<input type="date" value={startDate} onChange={e => setStartDate(e.target.value)} /></label><label>Đến ngày<input type="date" value={endDate} onChange={e => setEndDate(e.target.value)} /></label><button onClick={() => void load()} disabled={loading}>{loading ? 'Đang xem...' : 'Xem báo cáo'}</button><button className="csv-button" onClick={() => void download()} disabled={loading}>Tải CSV</button></div>
    {error && <p className="occupancy-error">{error}</p>}
    {report && <><div className="occupancy-filter type-filter"><label>Loại phòng<select value={roomTypeId} onChange={e => changeType(e.target.value)}><option value="">Tất cả loại phòng</option>{report.roomTypes.map(type => <option key={type.roomTypeId} value={type.roomTypeId}>{type.name}</option>)}</select></label></div>
      <div className="occupancy-summary"><article><span>Công suất trung bình kỳ</span><strong>{percent(report.totalAverageOccupancyPercent)}</strong><small>{report.totalSoldNights} đêm bán / {report.totalAvailableNights} đêm khả dụng</small></article>{report.roomTypes.map(type => <article key={type.roomTypeId}><span>{type.name}</span><strong>{percent(type.averageOccupancyPercent)}</strong><small>{type.soldNights} / {type.availableNights} đêm</small></article>)}</div>
      <h2>Công suất từng ngày — {report.selectedRoomTypeName}</h2><div className="occupancy-table-wrap"><table><thead><tr><th>Ngày</th><th>Đêm đã bán</th><th>Đêm khả dụng</th><th>Công suất</th></tr></thead><tbody>{report.dailyOccupancies.map(day => <tr key={day.date}><td>{day.date.split('-').reverse().join('/')}</td><td>{day.soldNights}</td><td>{day.availableNights}</td><td>{percent(day.occupancyPercent)}</td></tr>)}</tbody></table></div></>}

  const [report, setReport] = useState<OccupancyReport | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  async function load() {
    setError(null)
    if (!startDate || !endDate) return setError('Vui lòng chọn ngày bắt đầu và ngày kết thúc.')
    if (endDate < startDate) return setError('Ngày kết thúc phải bằng hoặc sau ngày bắt đầu.')
    if ((Date.parse(`${endDate}T00:00:00`) - Date.parse(`${startDate}T00:00:00`)) / 86_400_000 + 1 > 366) return setError('Khoảng ngày báo cáo không được vượt quá 366 ngày.')
    setLoading(true)
    try { setReport(await getOccupancyReport(startDate, endDate, roomTypeId)) }
    catch (reason) { setError(reason instanceof ApiRequestError ? reason.message : 'Không tải được báo cáo công suất.') }
    finally { setLoading(false) }
  }

  function changeType(value: string) {
    setRoomTypeId(value)
    if (report) void (async () => {
      setLoading(true); setError(null)
      try { setReport(await getOccupancyReport(startDate, endDate, value)) }
      catch (reason) { setError(reason instanceof ApiRequestError ? reason.message : 'Không tải được báo cáo công suất.') }
      finally { setLoading(false) }
    })()
  }

  const selected = report?.roomTypes.find(type => type.roomTypeId === Number(roomTypeId))
  return <section className="occupancy-report">
    <form className="occupancy-filter" onSubmit={event => { event.preventDefault(); void load() }}>
      <label>Ngày bắt đầu<input type="date" value={startDate} onChange={event => setStartDate(event.target.value)} /></label>
      <label>Ngày kết thúc<input type="date" value={endDate} onChange={event => setEndDate(event.target.value)} /></label>
      <button type="submit" disabled={loading}>{loading ? 'Đang tải…' : 'Xem báo cáo'}</button>
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
        <article><span>Công suất toàn bộ</span><strong>{percent(report.totalAverageOccupancyPercent)}</strong><small>{report.totalSoldNights} / {report.totalAvailableNights} đêm</small></article>
        {selected && <article><span>{selected.name}</span><strong>{percent(selected.averageOccupancyPercent)}</strong><small>{selected.soldNights} / {selected.availableNights} đêm</small></article>}
      </div>
      <div className="occupancy-types"><h2>Theo loại phòng</h2>{report.roomTypes.map(type => <article key={type.roomTypeId}><span>{type.code} · {type.name}</span><strong>{percent(type.averageOccupancyPercent)}</strong><small>{type.soldNights} đêm bán / {type.availableNights} đêm khả dụng</small></article>)}</div>
      <div className="occupancy-table"><table><caption>Công suất từng ngày {roomTypeId ? `— ${selected?.name ?? ''}` : '— tất cả loại phòng'}</caption><thead><tr><th>Ngày</th><th>Đêm đã bán</th><th>Đêm khả dụng</th><th>Công suất</th></tr></thead><tbody>{report.dailyOccupancies.map(day => <tr key={day.date}><td>{dateText(day.date)}</td><td>{day.soldNights}</td><td>{day.availableNights}</td><td>{percent(day.occupancyPercent)}</td></tr>)}</tbody></table></div>
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
