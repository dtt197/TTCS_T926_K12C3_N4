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

// ─── S4-09: Tài khoản mẫu theo vai trò ────────────────────────────────────────

export interface SampleAccount {
  roleCode: string
  roleTitle: string
  icon: string
  fullName: string
  email: string
  password: string
  badgeClass: string
  description: string
  permissions: string[]
}

export const SAMPLE_ACCOUNTS: SampleAccount[] = [
  {
    roleCode: 'OWNER',
    roleTitle: 'Chủ homestay',
    icon: '👑',
    fullName: 'Chủ homestay Demo',
    email: 'owner.demo@homestay.local',
    password: 'password',
    badgeClass: 'sdp-badge--owner',
    description: 'Chủ cơ sở, có toàn quyền xem báo cáo doanh thu, nạp dữ liệu mẫu và thiết lập chính sách giá.',
    permissions: [
      'Nạp lại bộ dữ liệu mẫu (S4-09)',
      'Báo cáo công suất phòng & doanh thu',
      'Cấu hình giá đè ngày lễ & chính sách hủy',
      'Quản lý loại phòng & tiện nghi',
      'Phê duyệt các yêu cầu bảo trì',
    ],
  },
  {
    roleCode: 'RECEPTIONIST',
    roleTitle: 'Lễ tân homestay',
    icon: '🛎️',
    fullName: 'Lễ tân Demo',
    email: 'letan.demo@homestay.local',
    password: 'password',
    badgeClass: 'sdp-badge--receptionist',
    description: 'Trực quầy tiếp đón, kiểm tra sơ đồ phòng, tạo đặt phòng, check-in, thêm phụ thu và check-out.',
    permissions: [
      'Tra cứu phòng trống & Sơ đồ lịch 14 ngày',
      'Tạo đặt phòng & xác nhận cọc',
      'Thực hiện Nhận phòng (Check-in)',
      'Thêm phụ thu lưu trú & Đổi phòng',
      'Thực hiện Trả phòng (Check-out) & Quyết toán',
    ],
  },
  {
    roleCode: 'HOUSEKEEPING',
    roleTitle: 'Nhân viên buồng phòng',
    icon: '🧹',
    fullName: 'Buồng phòng Demo',
    email: 'buongphong.demo@homestay.local',
    password: 'password',
    badgeClass: 'sdp-badge--housekeeping',
    description: 'Theo dõi danh sách phòng cần dọn dẹp, cập nhật trạng thái phòng sạch và báo cáo sự cố phòng.',
    permissions: [
      'Xem danh sách phòng cần dọn (Trống bẩn)',
      'Cập nhật phòng sang Trống sạch',
      'Ghi nhận và báo cáo sự cố hư hỏng phòng',
      'Theo dõi lịch dọn phòng theo ca',
    ],
  },
  {
    roleCode: 'ADMIN',
    roleTitle: 'Quản trị viên',
    icon: '⚙️',
    fullName: 'Demo Administrator',
    email: 'admin.demo@homestay.local',
    password: 'password',
    badgeClass: 'sdp-badge--admin',
    description: 'Quản trị kỹ thuật, quản lý tài khoản người dùng, nhật ký kiểm toán và ma trận bảo mật.',
    permissions: [
      'Quản lý tài khoản nội bộ & trạng thái',
      'Xem nhật ký kiểm toán hệ thống (Audit log)',
      'Cấu hình thông số vận hành chung',
      'Đối chiếu ma trận bảo mật quyền API',
    ],
  },
]

// ─── S4-09: Hướng dẫn trọn luồng Đặt phòng → Trả phòng ────────────────────────

export interface FlowStep {
  step: number
  title: string
  role: string
  roleBadgeClass: string
  description: string
  actions: { label: string; href: string }[]
  tips: string
}

export const FLOW_STEPS: FlowStep[] = [
  {
    step: 1,
    title: 'Nạp bộ dữ liệu mẫu chuẩn',
    role: 'Chủ homestay (OWNER)',
    roleBadgeClass: 'sdp-badge--owner',
    description: 'Bấm nút "Nạp lại dữ liệu mẫu" ở đầu trang để reset hệ thống về đúng 4 loại phòng, 20 phòng, bảng giá ngày lễ và 30 booking.',
    actions: [],
    tips: 'Giúp đưa homestay về trạng thái chuẩn sạch để sẵn sàng chạy thử nghiệm quy trình từ đầu.',
  },
  {
    step: 2,
    title: 'Tra cứu phòng trống & Tạo đặt phòng',
    role: 'Lễ tân (RECEPTIONIST)',
    roleBadgeClass: 'sdp-badge--receptionist',
    description: 'Đăng nhập tài khoản Lễ tân. Vào Lịch phòng (Calendar) hoặc Danh sách phòng để kiểm tra các phòng trống sạch (màu xanh). Sau đó vào Quản lý Booking tạo đặt phòng mới, chọn loại phòng, ngày đến/đi và xác nhận đặt cọc.',
    actions: [
      { label: 'Xem Lịch phòng (14 ngày)', href: '/rooms/calendar' },
      { label: 'Quản lý đặt phòng', href: '/bookings' },
    ],
    tips: 'Khách hàng cũng có thể tự tra cứu và đặt phòng tại trang công khai danh sách loại phòng.',
  },
  {
    step: 3,
    title: 'Thực hiện Nhận phòng (Check-in)',
    role: 'Lễ tân (RECEPTIONIST)',
    roleBadgeClass: 'sdp-badge--receptionist',
    description: 'Khi khách đến nhận phòng, Lễ tân vào Quản lý phòng, chọn phòng tương ứng đang ở trạng thái "Trống sạch" và bấm nút "Check-in". Hệ thống ghi nhận thời điểm nhận phòng và tự động đổi trạng thái phòng sang "Đang có khách".',
    actions: [
      { label: 'Quản lý phòng (Sơ đồ)', href: '/rooms' },
    ],
    tips: 'Chỉ phòng ở trạng thái "Trống sạch" mới được phép thực hiện thủ tục Check-in.',
  },
  {
    step: 4,
    title: 'Cập nhật lưu trú & Thêm phụ thu',
    role: 'Lễ tân (RECEPTIONIST)',
    roleBadgeClass: 'sdp-badge--receptionist',
    description: 'Trong quá trình khách lưu trú, nếu phát sinh dịch vụ (nhận phòng sớm, thêm người, nước uống/dịch vụ phát sinh), Lễ tân vào chi tiết booking bấm "Thêm phụ thu" (nhập loại và lý do bắt buộc). Có thể đổi phòng nếu khách yêu cầu.',
    actions: [
      { label: 'Danh sách Booking & Chi tiết', href: '/bookings' },
    ],
    tips: 'Phụ thu hỗ trợ nhập giá tuỳ chỉnh kèm lý do bắt buộc theo tiêu chuẩn S4-02.',
  },
  {
    step: 5,
    title: 'Trả phòng (Check-out) & Quyết toán',
    role: 'Lễ tân (RECEPTIONIST)',
    roleBadgeClass: 'sdp-badge--receptionist',
    description: 'Lễ tân bấm "Check-out" trên phòng hoặc trong booking. Hệ thống tự động tính toán tổng tiền đêm, đối trừ tiền đặt cọc và phụ thu. Sau khi xác nhận thanh toán, booking chuyển sang "Đã trả phòng" và phòng chuyển sang "Trống bẩn".',
    actions: [
      { label: 'Thực hiện Check-out phòng', href: '/rooms' },
    ],
    tips: 'Nếu khách trả phòng sớm hơn ngày dự kiến, hệ thống sẽ yêu cầu nhập lý do theo quy định.',
  },
  {
    step: 6,
    title: 'Dọn phòng & Đưa phòng về sẵn sàng',
    role: 'Buồng phòng (HOUSEKEEPING)',
    roleBadgeClass: 'sdp-badge--housekeeping',
    description: 'Đăng nhập tài khoản Buồng phòng. Nhân viên buồng phòng xem danh sách các phòng "Trống bẩn" cần dọn dẹp. Sau khi vệ sinh sạch sẽ, bấm cập nhật trạng thái phòng sang "Trống sạch" để hoàn tất trọn luồng chu trình lưu trú.',
    actions: [
      { label: 'Danh sách phòng buồng phòng', href: '/rooms' },
    ],
    tips: 'Phòng sau khi chuyển "Trống sạch" sẽ lập tức hiển thị sẵn sàng đón lượt khách tiếp theo.',
  },
]

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
  const [copiedKey, setCopiedKey] = useState<string | null>(null)

  function handleCopy(text: string, key: string) {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text)
    }
    setCopiedKey(key)
    setTimeout(() => {
      setCopiedKey((curr) => (curr === key ? null : curr))
    }, 2000)
  }

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

      {/* ── S4-09: Tài khoản mẫu theo vai trò ──────────────────────────── */}
      <section className="sdp-section sdp-accounts-panel" aria-label="Tài khoản mẫu theo vai trò">
        <div className="sdp-section-header">
          <div>
            <h3 className="sdp-section-heading">
              <span className="sdp-badge sdp-badge--s409">S4-09</span>
              Tài khoản mẫu theo vai trò
            </h3>
            <p className="sdp-section-sub">
              Sử dụng các tài khoản mẫu dưới đây để đăng nhập và thử nghiệm các quyền hạn riêng biệt theo vai trò.
            </p>
          </div>
        </div>

        <div className="sdp-accounts-grid">
          {SAMPLE_ACCOUNTS.map((acc) => (
            <div key={acc.roleCode} className={`sdp-account-card ${acc.badgeClass}`}>
              <div className="sdp-account-top">
                <span className="sdp-account-icon" aria-hidden="true">{acc.icon}</span>
                <div className="sdp-account-identity">
                  <h4 className="sdp-account-title">{acc.roleTitle}</h4>
                  <span className="sdp-account-code">ROLE_{acc.roleCode}</span>
                </div>
              </div>

              <p className="sdp-account-desc">{acc.description}</p>

              <div className="sdp-account-credentials">
                <div className="sdp-cred-row">
                  <span className="sdp-cred-label">Email:</span>
                  <code className="sdp-cred-val">{acc.email}</code>
                  <button
                    type="button"
                    className="sdp-btn-copy"
                    onClick={() => handleCopy(acc.email, `${acc.roleCode}-email`)}
                    title="Sao chép email"
                  >
                    {copiedKey === `${acc.roleCode}-email` ? '✓ Đã chép' : 'Sao chép'}
                  </button>
                </div>
                <div className="sdp-cred-row">
                  <span className="sdp-cred-label">Mật khẩu:</span>
                  <code className="sdp-cred-val">{acc.password}</code>
                  <button
                    type="button"
                    className="sdp-btn-copy"
                    onClick={() => handleCopy(acc.password, `${acc.roleCode}-pass`)}
                    title="Sao chép mật khẩu"
                  >
                    {copiedKey === `${acc.roleCode}-pass` ? '✓ Đã chép' : 'Sao chép'}
                  </button>
                </div>
              </div>

              <div className="sdp-account-perms">
                <span className="sdp-perms-title">Quyền hạn chính:</span>
                <ul className="sdp-perms-list">
                  {acc.permissions.map((p, idx) => (
                    <li key={idx}>{p}</li>
                  ))}
                </ul>
              </div>
            </div>
          ))}
        </div>
      </section>

      {/* ── S4-09: Hướng dẫn trọn luồng Đặt phòng → Trả phòng ───────────── */}
      <section className="sdp-section sdp-flow-panel" aria-label="Hướng dẫn chạy trọn luồng">
        <div className="sdp-section-header">
          <div>
            <h3 className="sdp-section-heading">
              <span className="sdp-badge sdp-badge--s409">S4-09</span>
              Hướng dẫn trọn luồng: Tra phòng → Đặt phòng → Nhận phòng → Trả phòng
            </h3>
            <p className="sdp-section-sub">
              Kịch bản nghiệm thu tuần tự các bước vận hành homestay trên bộ dữ liệu mẫu ban đầu.
            </p>
          </div>
        </div>

        <div className="sdp-flow-timeline">
          {FLOW_STEPS.map((step) => (
            <div key={step.step} className="sdp-flow-step">
              <div className="sdp-step-badge">
                <span className="sdp-step-num">{step.step}</span>
              </div>
              <div className="sdp-step-content">
                <div className="sdp-step-header">
                  <h4 className="sdp-step-title">{step.title}</h4>
                  <span className={`sdp-badge ${step.roleBadgeClass}`}>{step.role}</span>
                </div>
                <p className="sdp-step-desc">{step.description}</p>
                {step.tips && (
                  <div className="sdp-step-tip">
                    <span aria-hidden="true">💡</span> <em>{step.tips}</em>
                  </div>
                )}
                {step.actions.length > 0 && (
                  <div className="sdp-step-actions">
                    {step.actions.map((act) => (
                      <a key={act.href} href={act.href} className="sdp-flow-btn">
                        {act.label} <span aria-hidden="true">→</span>
                      </a>
                    ))}
                  </div>
                )}
              </div>
            </div>
          ))}
        </div>
      </section>

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
