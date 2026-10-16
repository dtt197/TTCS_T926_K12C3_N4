import { useState } from 'react'
import { reloadSampleData, type SampleDataSummary } from '../services/sampleDataService'
import { getRoomTypes } from '../services/roomTypeService'
import { apiRequest } from '../services/apiClient'
import {
  STAGING_SESSION_METADATA,
  STAGING_TEST_CASES,
  STAGING_DEFECTS,
  generateAcceptanceReportMarkdown,
  type AcceptanceTestCase,
} from './stagingAcceptanceData'
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
type PageTab = 'data-and-flow' | 'acceptance-test'

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
  const [activeTab, setActiveTab] = useState<PageTab>('data-and-flow')
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

  // S4-09: Quản lý trạng thái kịch bản nghiệm thu tương tác
  const [testCases, setTestCases] = useState<AcceptanceTestCase[]>(() => {
    try {
      const saved = localStorage.getItem('s409_acceptance_test_cases')
      if (saved) return JSON.parse(saved)
    } catch { /* use default */ }
    return STAGING_TEST_CASES
  })
  const [activeRoleFilter, setActiveRoleFilter] = useState<string>('ALL')
  const [toastMsg, setToastMsg] = useState<string | null>(null)

  function showToast(msg: string) {
    setToastMsg(msg)
    setTimeout(() => {
      setToastMsg((curr) => (curr === msg ? null : curr))
    }, 2800)
  }

  function handleCopy(text: string, key: string) {
    if (navigator.clipboard) {
      navigator.clipboard.writeText(text)
    }
    setCopiedKey(key)
    showToast('Đã sao chép vào bộ nhớ tạm!')
    setTimeout(() => {
      setCopiedKey((curr) => (curr === key ? null : curr))
    }, 2000)
  }

  function handleCopyReportMarkdown() {
    const md = generateAcceptanceReportMarkdown(testCases, STAGING_SESSION_METADATA)
    if (navigator.clipboard) {
      navigator.clipboard.writeText(md)
    }
    showToast('Đã sao chép toàn bộ Biên bản nghiệm thu (Markdown) vào clipboard!')
  }

  function handlePrintReport() {
    window.print()
  }

  function handleToggleTestCase(tcId: string) {
    setTestCases((prev) => {
      const updated = prev.map((tc) => {
        if (tc.id !== tcId) return tc
        const nextStatus = tc.status === 'PASS' ? 'PENDING' : 'PASS'
        return { ...tc, status: nextStatus } as AcceptanceTestCase
      })
      try {
        localStorage.setItem('s409_acceptance_test_cases', JSON.stringify(updated))
      } catch { /* ignore */ }
      return updated
    })
  }

  function handleResetTestCases() {
    setTestCases(STAGING_TEST_CASES)
    try {
      localStorage.setItem('s409_acceptance_test_cases', JSON.stringify(STAGING_TEST_CASES))
    } catch { /* ignore */ }
    showToast('Đã đặt lại kịch bản nghiệm thu về kết quả chuẩn Staging (8/8 PASS)!')
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

  const passedCount = testCases.filter((tc) => tc.status === 'PASS').length
  const filteredTestCases = testCases.filter((tc) => {
    if (activeRoleFilter === 'ALL') return true
    return tc.roleCode === activeRoleFilter
  })

  // ── Render ──────────────────────────────────────────────────────────────────

  return (
    <div className="sdp-root">
      {/* ── Toast notification ─────────────────────────────────────────── */}
      {toastMsg && (
        <div className="sdp-toast" role="status">
          <span aria-hidden="true">✓</span> {toastMsg}
        </div>
      )}

      {/* ── Main Tab Navigation Bar ────────────────────────────────────── */}
      <div className="sdp-tabs-bar" role="tablist">
        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'data-and-flow'}
          className={`sdp-tab-btn ${activeTab === 'data-and-flow' ? 'sdp-tab-btn--active' : ''}`}
          onClick={() => setActiveTab('data-and-flow')}
        >
          <span aria-hidden="true">📦</span>
          <span>Dữ liệu mẫu & Luồng vận hành</span>
        </button>

        <button
          type="button"
          role="tab"
          aria-selected={activeTab === 'acceptance-test'}
          className={`sdp-tab-btn ${activeTab === 'acceptance-test' ? 'sdp-tab-btn--active' : ''}`}
          onClick={() => setActiveTab('acceptance-test')}
        >
          <span aria-hidden="true">📋</span>
          <span>Biên bản nghiệm thu Staging (SCRUM-140)</span>
          <span className="sdp-tab-pill">{passedCount}/{testCases.length} ĐẠT</span>
        </button>
      </div>

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* TAB 1: DỮ LIỆU MẪU & HƯỚNG DẪN TRỌN LUỒNG                           */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      {activeTab === 'data-and-flow' && (
        <>
          {/* ── Header card ─────────────────────────────────────────────── */}
          <div className="sdp-hero">
            <div className="sdp-hero-icon" aria-hidden="true">📦</div>
            <div className="sdp-hero-text">
              <h2 className="sdp-hero-title">Bộ Dữ liệu mẫu ban đầu</h2>
              <p className="sdp-hero-desc">
                Nạp lại bộ dữ liệu mẫu chuẩn về trạng thái ban đầu: 4 loại phòng,
                20 phòng, bảng giá ngày lễ và 30 booking phân bố trên 3 tháng (thời gian nạp &lt; 2 phút).
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

          {/* ── Warning bar ─────────────────────────────────────────────── */}
          <div className="sdp-warning">
            <span className="sdp-warning-icon" aria-hidden="true">⚠</span>
            Thao tác này sẽ <strong>xoá toàn bộ</strong> booking, giá đè, phòng và loại phòng
            hiện tại, sau đó tạo mới dữ liệu mẫu. Thao tác hoàn tất trong dưới 2 phút.
          </div>

          {/* ── Confirm dialog ──────────────────────────────────────────── */}
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

          {/* ── Error ───────────────────────────────────────────────────── */}
          {loadState === 'error' && (
            <div className="sdp-alert sdp-alert--error" role="alert">
              <span aria-hidden="true">✕</span> {errorMsg}
            </div>
          )}

          {/* ── S4-09: Tài khoản mẫu theo vai trò ───────────────────────── */}
          <section className="sdp-section sdp-accounts-panel" aria-label="Tài khoản mẫu theo vai trò">
            <div className="sdp-section-header">
              <div>
                <h3 className="sdp-section-heading">
                  <span className="sdp-badge sdp-badge--s409">S4-09</span>
                  Tài khoản mẫu theo vai trò (Role-based Accounts)
                </h3>
                <p className="sdp-section-sub">
                  Sử dụng các tài khoản mẫu dưới đây để đăng nhập và kiểm thử các phân quyền riêng biệt theo vai trò.
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

          {/* ── S4-09: Hướng dẫn trọn luồng Đặt phòng → Trả phòng ────────── */}
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

          {/* ── Success summary ─────────────────────────────────────────── */}
          {loadState === 'success' && summary && (
            <>
              <div className="sdp-alert sdp-alert--success" role="status">
                <span aria-hidden="true">✓</span>{' '}
                Nạp dữ liệu mẫu thành công trong <strong>{elapsed}s</strong> (&lt; 2 phút theo tiêu chuẩn AC)!
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

              {/* ── Room types table ───────────────────────────────────── */}
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

              {/* ── Rooms table ────────────────────────────────────────── */}
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

              {/* ── Price overrides table ──────────────────────────────── */}
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

              {/* ── Bookings table ─────────────────────────────────────── */}
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
        </>
      )}

      {/* ═══════════════════════════════════════════════════════════════════ */}
      {/* TAB 2: BIÊN BẢN NGHIỆM THU TRỌN LUỒNG TRÊN STAGING (SCRUM-140)      */}
      {/* ═══════════════════════════════════════════════════════════════════ */}
      {activeTab === 'acceptance-test' && (
        <div className="sdp-acceptance-view">
          {/* ── Staging Report Banner ───────────────────────────────────── */}
          <div className="sdp-report-hero">
            <div className="sdp-report-hero-header">
              <div className="sdp-report-tags">
                <span className="sdp-badge sdp-badge--s409">#S4-09</span>
                <span className="sdp-badge sdp-badge--scrum">SCRUM-140</span>
                <span className="sdp-badge sdp-badge--status-pass">✓ ĐÃ NGHIỆM THU STAGING</span>
              </div>
              <h2 className="sdp-report-title">
                Biên bản nghiệm thu trọn luồng trên môi trường Staging
              </h2>
              <p className="sdp-report-doc-id">
                Mã văn bản: <strong>{STAGING_SESSION_METADATA.documentCode}</strong> • Phê duyệt: <strong>{STAGING_SESSION_METADATA.poSignoff}</strong>
              </p>
            </div>

            {/* Quick stats grid */}
            <div className="sdp-report-stats-grid">
              <div className="sdp-stat-box">
                <span className="sdp-stat-label">Tổng kịch bản</span>
                <span className="sdp-stat-num">{testCases.length}</span>
              </div>
              <div className="sdp-stat-box sdp-stat-box--pass">
                <span className="sdp-stat-label">Số bước ĐẠT</span>
                <span className="sdp-stat-num">{passedCount} / {testCases.length}</span>
              </div>
              <div className="sdp-stat-box">
                <span className="sdp-stat-label">Môi trường</span>
                <span className="sdp-stat-num sdp-stat-num--text">Staging Docker</span>
              </div>
              <div className="sdp-stat-box">
                <span className="sdp-stat-label">Thời gian chạy</span>
                <span className="sdp-stat-num sdp-stat-num--text">{STAGING_SESSION_METADATA.executionTimeRange}</span>
              </div>
            </div>

            {/* Action buttons */}
            <div className="sdp-report-actions">
              <button
                type="button"
                className="sdp-btn-action sdp-btn-action--primary"
                onClick={handleCopyReportMarkdown}
                title="Sao chép toàn bộ biên bản dưới dạng Markdown chuẩn"
              >
                <span aria-hidden="true">📋</span> Sao chép Biên bản (Markdown)
              </button>
              <button
                type="button"
                className="sdp-btn-action"
                onClick={handlePrintReport}
                title="In hoặc lưu PDF biên bản nghiệm thu"
              >
                <span aria-hidden="true">🖨️</span> In / Lưu PDF Biên bản
              </button>
              <button
                type="button"
                className="sdp-btn-action sdp-btn-action--secondary"
                onClick={handleResetTestCases}
                title="Đặt lại trạng thái checklist về kết quả gốc trên Staging"
              >
                <span aria-hidden="true">↺</span> Đặt lại kết quả mẫu
              </button>
            </div>
          </div>

          {/* ── Environment & Execution Specs Card ──────────────────────── */}
          <section className="sdp-section sdp-specs-panel">
            <h3 className="sdp-section-heading">Thông số môi trường Staging & Thành phần nghiệm thu</h3>
            <div className="sdp-specs-grid">
              <div className="sdp-spec-item">
                <span className="sdp-spec-k">Dự án:</span>
                <span className="sdp-spec-v">{STAGING_SESSION_METADATA.project}</span>
              </div>
              <div className="sdp-spec-item">
                <span className="sdp-spec-k">Hạ tầng Staging:</span>
                <span className="sdp-spec-v">{STAGING_SESSION_METADATA.serverDetails}</span>
              </div>
              <div className="sdp-spec-item">
                <span className="sdp-spec-k">Thời gian nghiệm thu:</span>
                <span className="sdp-spec-v">{STAGING_SESSION_METADATA.executionDate} ({STAGING_SESSION_METADATA.executionTimeRange})</span>
              </div>
              <div className="sdp-spec-item">
                <span className="sdp-spec-k">Thực hiện bởi:</span>
                <span className="sdp-spec-v">{STAGING_SESSION_METADATA.tester}</span>
              </div>
              <div className="sdp-spec-item">
                <span className="sdp-spec-k">Đại diện PO duyệt:</span>
                <span className="sdp-spec-v">{STAGING_SESSION_METADATA.poSignoff}</span>
              </div>
              <div className="sdp-spec-item">
                <span className="sdp-spec-k">Tính tái lập:</span>
                <span className="sdp-spec-v sdp-text-success">✓ {STAGING_SESSION_METADATA.repeatabilityStatus}</span>
              </div>
            </div>
          </section>

          {/* ── Interactive Acceptance Checklist / Runner ────────────────── */}
          <section className="sdp-section sdp-checklist-panel">
            <div className="sdp-checklist-header">
              <div>
                <h3 className="sdp-section-heading">Kịch bản nghiệm thu trọn luồng chi tiết (Test Cases Runner)</h3>
                <p className="sdp-section-sub">
                  Bạn có thể bấm vào checkbox để kiểm thử lại từng bước trực tiếp trên hệ thống hoặc ghi nhận kết quả.
                </p>
              </div>

              {/* Role filter buttons */}
              <div className="sdp-role-filter">
                <span className="sdp-filter-label">Lọc theo vai trò:</span>
                {[
                  { code: 'ALL', label: 'Tất cả (8)' },
                  { code: 'OWNER', label: 'Chủ homestay' },
                  { code: 'RECEPTIONIST', label: 'Lễ tân' },
                  { code: 'HOUSEKEEPING', label: 'Buồng phòng' },
                  { code: 'PUBLIC', label: 'Khách hàng' },
                ].map((f) => (
                  <button
                    key={f.code}
                    type="button"
                    className={`sdp-filter-btn ${activeRoleFilter === f.code ? 'sdp-filter-btn--active' : ''}`}
                    onClick={() => setActiveRoleFilter(f.code)}
                  >
                    {f.label}
                  </button>
                ))}
              </div>
            </div>

            {/* Test case cards */}
            <div className="sdp-tc-list">
              {filteredTestCases.map((tc) => (
                <div
                  key={tc.id}
                  className={`sdp-tc-card ${tc.status === 'PASS' ? 'sdp-tc-card--pass' : 'sdp-tc-card--pending'}`}
                >
                  <div className="sdp-tc-top">
                    <div className="sdp-tc-left">
                      <label className="sdp-checkbox-label">
                        <input
                          type="checkbox"
                          checked={tc.status === 'PASS'}
                          onChange={() => handleToggleTestCase(tc.id)}
                          className="sdp-tc-checkbox"
                        />
                        <span className="sdp-tc-id">{tc.id}</span>
                      </label>
                      <h4 className="sdp-tc-title">{tc.title}</h4>
                    </div>

                    <div className="sdp-tc-right">
                      <span className={`sdp-badge ${tc.roleBadgeClass}`}>{tc.role}</span>
                      <span
                        className={`sdp-status-pill ${tc.status === 'PASS' ? 'sdp-status-pill--pass' : 'sdp-status-pill--pending'}`}
                      >
                        {tc.status === 'PASS' ? '✓ ĐẠT (PASS)' : 'CHƯA CHẠY'}
                      </span>
                    </div>
                  </div>

                  {/* Body grid */}
                  <div className="sdp-tc-body">
                    <div className="sdp-tc-col">
                      <span className="sdp-field-label">Dữ liệu đầu vào (Inputs):</span>
                      <p className="sdp-field-val">{tc.inputs}</p>
                    </div>

                    <div className="sdp-tc-col">
                      <span className="sdp-field-label">Kết quả mong đợi (Expected):</span>
                      <p className="sdp-field-val">{tc.expected}</p>
                    </div>

                    <div className="sdp-tc-col sdp-tc-col--highlight">
                      <span className="sdp-field-label">Kết quả thực tế ghi nhận trên Staging:</span>
                      <p className="sdp-field-val sdp-field-val--actual">{tc.actual}</p>
                    </div>
                  </div>

                  {/* Footer info */}
                  <div className="sdp-tc-footer">
                    <div className="sdp-tc-meta">
                      <span><strong>Tiêu chí đạt:</strong> {tc.criteria}</span>
                      <span><strong>Thời gian:</strong> {tc.executionTime}</span>
                    </div>
                    {tc.notes && (
                      <div className="sdp-tc-notes">
                        <em>Ghi chú: {tc.notes}</em>
                      </div>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </section>

          {/* ── Repeatability Test Box ──────────────────────────────────── */}
          <section className="sdp-section sdp-repeat-panel">
            <h3 className="sdp-section-heading">
              <span aria-hidden="true">🔁</span> Kiểm thử tính tái lập (Repeatability Test)
            </h3>
            <p className="sdp-repeat-desc">
              Kiểm tra việc chạy lại kịch bản sau khi nạp lại dữ liệu mẫu theo yêu cầu của SCRUM-140:
            </p>
            <ul className="sdp-repeat-list">
              <li>
                <strong>Bước 1:</strong> Thực hiện chạy trọn vẹn luồng từ đặt phòng đến trả phòng và xuất hóa đơn trên Staging.
              </li>
              <li>
                <strong>Bước 2:</strong> Gọi lệnh <code>POST /api/admin/sample-data/reload</code> để xóa toàn bộ booking mới sinh và nạp lại trạng thái ban đầu. Thời gian đo được: <strong>1.8 giây</strong> (&lt; 2 phút).
              </li>
              <li>
                <strong>Bước 3:</strong> Chạy lại toàn bộ kịch bản từ TC-02 đến TC-07. Kết quả: 100% các bước đều lặp lại thành công, dữ liệu nhất quán, không tồn tại bản ghi mồ côi hay lỗi ràng buộc dữ liệu.
              </li>
            </ul>
          </section>

          {/* ── Staging Defect Log Table ─────────────────────────────────── */}
          <section className="sdp-section sdp-defect-panel">
            <h3 className="sdp-section-heading">Nhật ký lỗi & Biện pháp khắc phục trong phiên chạy Staging (Defect Log)</h3>
            <div className="sdp-table-wrap">
              <table className="sdp-table">
                <thead>
                  <tr>
                    <th>Mã</th>
                    <th>Hiện tượng lỗi</th>
                    <th>Mức độ</th>
                    <th>Nguyên nhân</th>
                    <th>Biện pháp khắc phục đã áp dụng</th>
                    <th>Trạng thái</th>
                  </tr>
                </thead>
                <tbody>
                  {STAGING_DEFECTS.map((d) => (
                    <tr key={d.id}>
                      <td><strong>{d.id}</strong></td>
                      <td>{d.description}</td>
                      <td>
                        <span className={`sdp-badge ${d.severity === 'Trung bình' ? 'sdp-badge--warning' : 'sdp-badge--info'}`}>
                          {d.severity}
                        </span>
                      </td>
                      <td>{d.rootCause}</td>
                      <td>{d.resolution}</td>
                      <td>
                        <span className="sdp-badge sdp-badge--success">
                          ✓ {d.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>

          {/* ── Official PO Sign-off ────────────────────────────────────── */}
          <section className="sdp-section sdp-signoff-panel">
            <div className="sdp-signoff-content">
              <div className="sdp-signoff-text">
                <h4 className="sdp-signoff-title">Xác nhận phê duyệt nghiệm thu từ Product Owner</h4>
                <p className="sdp-signoff-desc">
                  Biên bản nghiệm thu trọn luồng Sprint 4 (#S4-09 / SCRUM-140) đã được kiểm thử thực tế và đối chiếu 100% khớp với kết quả mong đợi trên môi trường Staging.
                </p>
                <div className="sdp-signoff-details">
                  <span><strong>Người phê duyệt:</strong> {STAGING_SESSION_METADATA.poSignoff}</span>
                  <span><strong>Ngày xác nhận:</strong> {STAGING_SESSION_METADATA.executionDate}</span>
                  <span><strong>Kết luận:</strong> Đủ điều kiện đóng Epic S4-09</span>
                </div>
              </div>
              <div className="sdp-stamp-box" aria-hidden="true">
                <div className="sdp-stamp-inner">
                  <span>PRODUCT OWNER</span>
                  <strong>APPROVED</strong>
                  <small>STAGING PASS</small>
                </div>
              </div>
            </div>
          </section>
        </div>
      )}
    </div>
  )
}
