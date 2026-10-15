import { useState } from 'react'
import { reloadSampleData, type SampleDataSummary } from '../services/sampleDataService'
import { getRoomTypes } from '../services/roomTypeService'
import { apiRequest } from '../services/apiClient'
import './SampleDataPage.css'

/** Đo thời gian nạp dữ liệu; để ngoài component vì react-hooks/purity cấm gọi performance.now() trong component. */
function startTimer(): number {
  return performance.now()
}

function secondsSince(start: number): number {
  return Math.round((performance.now() - start) / 1000)
}

// ─── Types ────────────────────────────────────────────────────────────────────

interface RoomSummary {
  roomNumber: string
  floor: number
  roomType: string
  status: string
}

interface PriceOverrideSummary {
  id: number
  name: string
  roomTypeName: string
  startDate: string
  endDate: string
  pricePerNight: number
}

interface BookingSummary {
  bookingCode: string
  guestName: string
  roomTypeNameSnapshot: string
  checkInDate: string
  checkOutDate: string
  status: string
  totalAmount: number
}

type LoadState = 'idle' | 'loading' | 'success' | 'error'

const STATUS_LABEL: Record<string, string> = {
  TRONG_SACH:  'Trống sạch',
  TRONG_BAN:   'Trống bẩn',
  DANG_O:      'Đang có khách',
  BAO_TRI:     'Bảo trì',
}

const BOOKING_STATUS_LABEL: Record<string, string> = {
  CHO_XAC_NHAN:  'Chờ xác nhận',
  DA_XAC_NHAN:   'Đã xác nhận',
  DA_HUY:        'Đã huỷ',
  DA_NHAN_PHONG: 'Đã nhận phòng',
  DA_TRA_PHONG:  'Đã trả phòng',
  DA_HET_HAN:    'Đã hết hạn',
}

function fmtVnd(amount: number) {
  return amount.toLocaleString('vi-VN', { style: 'currency', currency: 'VND' })
}

// ─── Component ────────────────────────────────────────────────────────────────

export function SampleDataPage() {
  const [loadState, setLoadState] = useState<LoadState>('idle')
  const [summary, setSummary]     = useState<SampleDataSummary | null>(null)
  const [errorMsg, setErrorMsg]   = useState('')
  const [elapsed, setElapsed]     = useState<number | null>(null)

  // Danh sách hiển thị sau khi nạp
  const [roomTypes, setRoomTypes] = useState<{ id: number; code: string; name: string; weekdayPrice: number; weekendPrice: number; status: boolean }[]>([])
  const [rooms,     setRooms]     = useState<RoomSummary[]>([])
  const [prices,    setPrices]    = useState<PriceOverrideSummary[]>([])
  const [bookings,  setBookings]  = useState<BookingSummary[]>([])

  const [confirmOpen, setConfirmOpen] = useState(false)

  // ── Handlers ────────────────────────────────────────────────────────────────

  async function handleReload() {
    setConfirmOpen(false)
    setLoadState('loading')
    setErrorMsg('')
    setSummary(null)
    setRoomTypes([])
    setRooms([])
    setPrices([])
    setBookings([])

    const t0 = startTimer()

    try {
      const result = await reloadSampleData()
      setSummary(result)

      // Tải dữ liệu để hiển thị
      await Promise.all([
        fetchRoomTypes(),
        fetchRooms(),
        fetchPriceOverrides(),
        fetchBookings(),
      ])

      setElapsed(secondsSince(t0))
      setLoadState('success')
    } catch (err) {
      setErrorMsg(err instanceof Error ? err.message : 'Có lỗi xảy ra')
      setLoadState('error')
    }
  }

  async function fetchRoomTypes() {
    try {
      const raw = await getRoomTypes()
      const list = Array.isArray(raw) ? raw : (raw as { data?: unknown[] }).data ?? []
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      setRoomTypes((list as any[]).map((rt: any) => ({
        id:           rt.id,
        code:         rt.code,
        name:         rt.name,
        weekdayPrice: rt.weekdayPrice ?? 0,
        weekendPrice: rt.weekendPrice ?? 0,
        status:       rt.status,
      })))
    } catch { /* silently ignore */ }
  }

  async function fetchRooms() {
    try {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const data = await apiRequest<any[]>('/api/rooms')
      setRooms(data.map((r) => ({
        roomNumber: r.roomNumber,
        floor:      r.floor,
        roomType:   r.roomType,
        status:     r.status,
      })))
    } catch { /* silently ignore */ }
  }

  async function fetchPriceOverrides() {
    try {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const data = await apiRequest<any[]>('/api/price-overrides')
      setPrices(data.map((p) => ({
        id:             p.id,
        name:           p.name,
        roomTypeName:   p.roomTypeName ?? p.roomType?.name ?? '',
        startDate:      p.startDate,
        endDate:        p.endDate,
        pricePerNight:  p.pricePerNight,
      })))
    } catch { /* silently ignore */ }
  }

  async function fetchBookings() {
    try {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const data = await apiRequest<any>('/api/bookings?size=30&page=0')
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const list: any[] = data.content ?? data ?? []
      setBookings(list.map((b) => ({
        bookingCode:          b.bookingCode,
        guestName:            b.guestName,
        roomTypeNameSnapshot: b.roomTypeNameSnapshot,
        checkInDate:          b.checkInDate,
        checkOutDate:         b.checkOutDate,
        status:               b.status,
        totalAmount:          b.totalAmount ?? 0,
      })))
    } catch { /* silently ignore */ }
  }

  // ── Render ──────────────────────────────────────────────────────────────────

  return (
    <div className="sdp-root">
      {/* ── Header card ────────────────────────────────────────────────── */}
      <div className="sdp-hero">
        <div className="sdp-hero-icon" aria-hidden="true">📦</div>
        <div className="sdp-hero-text">
          <h2 className="sdp-hero-title">Dữ liệu mẫu</h2>
          <p className="sdp-hero-desc">
            Nạp lại bộ dữ liệu mẫu chuẩn về trạng thái ban đầu: 4 loại phòng,
            20 phòng, bảng giá ngày lễ và 30 booking phân bố trên 3 tháng.
          </p>
        </div>
        <button
          id="btn-reload-sample-data"
          type="button"
          className="sdp-btn-reload"
          disabled={loadState === 'loading'}
          onClick={() => setConfirmOpen(true)}
        >
          {loadState === 'loading' ? (
            <><span className="sdp-spinner" aria-hidden="true" />Đang nạp…</>
          ) : (
            <><span aria-hidden="true">↺</span> Nạp lại dữ liệu mẫu</>
          )}
        </button>
      </div>

      {/* ── Warning bar ────────────────────────────────────────────────── */}
      <div className="sdp-warning">
        <span className="sdp-warning-icon" aria-hidden="true">⚠</span>
        Thao tác này sẽ <strong>xoá toàn bộ</strong> booking, giá đè, phòng và loại phòng
        hiện tại, sau đó tạo mới dữ liệu mẫu. Không thể hoàn tác.
      </div>

      {/* ── Confirm dialog ─────────────────────────────────────────────── */}
      {confirmOpen && (
        <div className="sdp-overlay" role="dialog" aria-modal="true" aria-labelledby="sdp-confirm-title">
          <div className="sdp-dialog">
            <h3 id="sdp-confirm-title" className="sdp-dialog-title">
              Xác nhận nạp lại dữ liệu mẫu?
            </h3>
            <p className="sdp-dialog-body">
              Toàn bộ booking, giá đè, phòng và loại phòng sẽ bị <strong>xoá</strong> và
              thay bằng dữ liệu mẫu chuẩn. Thao tác không thể hoàn tác.
            </p>
            <div className="sdp-dialog-actions">
              <button
                id="btn-confirm-cancel"
                type="button"
                className="sdp-btn-cancel"
                onClick={() => setConfirmOpen(false)}
              >
                Huỷ
              </button>
              <button
                id="btn-confirm-reload"
                type="button"
                className="sdp-btn-confirm"
                onClick={handleReload}
              >
                Xác nhận nạp lại
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Error ──────────────────────────────────────────────────────── */}
      {loadState === 'error' && (
        <div className="sdp-alert sdp-alert--error" role="alert">
          <span aria-hidden="true">✕</span> {errorMsg}
        </div>
      )}

      {/* ── Success summary ────────────────────────────────────────────── */}
      {loadState === 'success' && summary && (
        <>
          <div className="sdp-alert sdp-alert--success" role="status">
            <span aria-hidden="true">✓</span>{' '}
            Nạp dữ liệu mẫu thành công trong <strong>{elapsed}s</strong>!
          </div>

          {/* Stat cards */}
          <div className="sdp-stats">
            {[
              { label: 'Loại phòng', value: summary.roomTypeCount,      icon: '▦' },
              { label: 'Phòng',      value: summary.roomCount,           icon: '▣' },
              { label: 'Bảng giá',   value: summary.priceOverrideCount,  icon: '₫' },
              { label: 'Booking',    value: summary.bookingCount,         icon: '▤' },
            ].map((s) => (
              <div key={s.label} className="sdp-stat-card">
                <span className="sdp-stat-icon" aria-hidden="true">{s.icon}</span>
                <span className="sdp-stat-value">{s.value}</span>
                <span className="sdp-stat-label">{s.label}</span>
              </div>
            ))}
          </div>

          {/* ── Room types table ──────────────────────────────────────── */}
          {roomTypes.length > 0 && (
            <section className="sdp-section">
              <h3 className="sdp-section-title">Loại phòng ({roomTypes.length})</h3>
              <div className="sdp-table-wrap">
                <table className="sdp-table" id="table-room-types">
                  <thead>
                    <tr>
                      <th>Mã</th>
                      <th>Tên loại phòng</th>
                      <th>Giá ngày thường</th>
                      <th>Giá cuối tuần</th>
                      <th>Trạng thái</th>
                    </tr>
                  </thead>
                  <tbody>
                    {roomTypes.map((rt) => (
                      <tr key={rt.id}>
                        <td><code>{rt.code}</code></td>
                        <td>{rt.name}</td>
                        <td className="sdp-td-num">{fmtVnd(rt.weekdayPrice)}</td>
                        <td className="sdp-td-num">{fmtVnd(rt.weekendPrice)}</td>
                        <td>
                          <span className={`sdp-badge ${rt.status ? 'sdp-badge--active' : 'sdp-badge--inactive'}`}>
                            {rt.status ? 'Đang bán' : 'Ngừng bán'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}

          {/* ── Rooms table ───────────────────────────────────────────── */}
          {rooms.length > 0 && (
            <section className="sdp-section">
              <h3 className="sdp-section-title">Phòng ({rooms.length})</h3>
              <div className="sdp-table-wrap">
                <table className="sdp-table" id="table-rooms">
                  <thead>
                    <tr>
                      <th>Số phòng</th>
                      <th>Tầng</th>
                      <th>Loại phòng</th>
                      <th>Trạng thái</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rooms.map((r) => (
                      <tr key={r.roomNumber}>
                        <td><strong>{r.roomNumber}</strong></td>
                        <td>Tầng {r.floor}</td>
                        <td>{r.roomType}</td>
                        <td>
                          <span className="sdp-status-dot" data-status={r.status} aria-label={STATUS_LABEL[r.status] ?? r.status} />
                          {STATUS_LABEL[r.status] ?? r.status}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}

          {/* ── Price overrides table ─────────────────────────────────── */}
          {prices.length > 0 && (
            <section className="sdp-section">
              <h3 className="sdp-section-title">Bảng giá ngày lễ ({prices.length})</h3>
              <div className="sdp-table-wrap">
                <table className="sdp-table" id="table-price-overrides">
                  <thead>
                    <tr>
                      <th>Tên đợt</th>
                      <th>Loại phòng</th>
                      <th>Từ ngày</th>
                      <th>Đến ngày</th>
                      <th>Giá / đêm</th>
                    </tr>
                  </thead>
                  <tbody>
                    {prices.map((p) => (
                      <tr key={p.id}>
                        <td>{p.name}</td>
                        <td>{p.roomTypeName}</td>
                        <td>{p.startDate}</td>
                        <td>{p.endDate}</td>
                        <td className="sdp-td-num">{fmtVnd(p.pricePerNight)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}

          {/* ── Bookings table ────────────────────────────────────────── */}
          {bookings.length > 0 && (
            <section className="sdp-section">
              <h3 className="sdp-section-title">Booking ({bookings.length})</h3>
              <div className="sdp-table-wrap">
                <table className="sdp-table" id="table-bookings">
                  <thead>
                    <tr>
                      <th>Mã booking</th>
                      <th>Khách</th>
                      <th>Loại phòng</th>
                      <th>Nhận phòng</th>
                      <th>Trả phòng</th>
                      <th>Tổng tiền</th>
                      <th>Trạng thái</th>
                    </tr>
                  </thead>
                  <tbody>
                    {bookings.map((b) => (
                      <tr key={b.bookingCode}>
                        <td><code>{b.bookingCode}</code></td>
                        <td>{b.guestName}</td>
                        <td>{b.roomTypeNameSnapshot}</td>
                        <td>{b.checkInDate}</td>
                        <td>{b.checkOutDate}</td>
                        <td className="sdp-td-num">{fmtVnd(b.totalAmount)}</td>
                        <td>
                          <span className={`sdp-badge sdp-badge--booking-${b.status.toLowerCase()}`}>
                            {BOOKING_STATUS_LABEL[b.status] ?? b.status}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}
        </>
      )}
    </div>
  )
}
