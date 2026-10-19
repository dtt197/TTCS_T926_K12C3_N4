/**
 * S4-09 AC4 (SCRUM-140): kịch bản nghiệm thu trọn luồng chạy thử trên staging (Docker Compose, S4-10).
 * Khớp từng bước với docs/staging-acceptance-test-report.md. Kết quả thực tế để trống, người chạy thử
 * ghi lại ngay trên trang "Kịch bản nghiệm thu" rồi sao chép biên bản Markdown vào tài liệu.
 */

export interface AcceptanceTestCase {
  id: string
  title: string
  role: string
  roleCode: 'OWNER' | 'RECEPTIONIST' | 'HOUSEKEEPING' | 'ADMIN' | 'PUBLIC'
  roleBadgeClass: string
  inputs: string
  expected: string
  actual: string
  executionTime: string
  status: 'PASS' | 'FAIL' | 'PENDING'
  criteria: string
  notes?: string
}

export interface StagingDefect {
  id: string
  description: string
  severity: 'Cao' | 'Trung bình' | 'Thấp'
  rootCause: string
  resolution: string
  status: 'Đã giải quyết' | 'Đang xử lý'
}

export interface StagingAcceptanceSession {
  documentCode: string
  project: string
  epic: string
  story: string
  environment: string
  serverDetails: string
  executionDate: string
  executionTimeRange: string
  tester: string
  poSignoff: string
  totalTests: number
  passedTests: number
  failedTests: number
  repeatabilityStatus: string
  overallStatus: 'APPROVED' | 'IN_REVIEW' | 'REJECTED'
}

/** Khoá lưu kết quả đang ghi trên trình duyệt (đổi khoá để bỏ kết quả mẫu cũ chạy trên máy local). */
export const ACCEPTANCE_STORAGE_KEY = 's409_staging_acceptance_v2'

export const STAGING_TEST_CASES: AcceptanceTestCase[] = [
  {
    id: 'TC-01',
    title: 'Nạp lại dữ liệu mẫu',
    role: 'Chủ homestay (OWNER)',
    roleCode: 'OWNER',
    roleBadgeClass: 'sdp-badge--owner',
    inputs: 'Menu Dữ liệu mẫu → Nạp lại dữ liệu mẫu (sau khi đã nhập địa chỉ, số điện thoại homestay ở Tham số).',
    expected: 'Thông báo 4 loại phòng, 20 phòng, bảng giá ngày lễ, 30 booking rải trên 3 tháng; thời gian nạp < 2 phút.',
    actual: '',
    executionTime: '',
    status: 'PENDING',
    criteria: 'S4-09 AC1, AC2',
  },
  {
    id: 'TC-02',
    title: 'Xem loại phòng, tra phòng trống và giá tạm tính trên điện thoại',
    role: 'Khách (PUBLIC)',
    roleCode: 'PUBLIC',
    roleBadgeClass: 'sdp-badge--public',
    inputs: 'Màn hình 360px: Danh sách loại phòng → Tra phòng trống 2 đêm tới, 2 khách → xem giá từng đêm.',
    expected: 'Danh sách có ảnh và giá; kết quả tra < 2 giây; giá từng đêm có nhãn ngày thường / cuối tuần / lễ.',
    actual: '',
    executionTime: '',
    status: 'PENDING',
    criteria: 'S2-03, S2-04, S2-05, S2-06',
  },
  {
    id: 'TC-03',
    title: 'Đặt phòng trực tuyến và nhận email tiếp nhận',
    role: 'Khách (PUBLIC)',
    roleCode: 'PUBLIC',
    roleBadgeClass: 'sdp-badge--public',
    inputs: 'Đặt phòng với email khach.test@example.com (hoặc email thật của người chạy thử).',
    expected: 'Có mã booking 8 ký tự, trạng thái chờ xác nhận; Mailpit có email tiếp nhận đặt phòng kèm liên kết tra cứu.',
    actual: '',
    executionTime: '',
    status: 'PENDING',
    criteria: 'S2-07, S4-04 AC1–AC2',
  },
  {
    id: 'TC-04',
    title: 'Tra cứu booking bằng mã và email',
    role: 'Khách (PUBLIC)',
    roleCode: 'PUBLIC',
    roleBadgeClass: 'sdp-badge--public',
    inputs: 'Bấm liên kết trong email ở TC-03, nhập email đã dùng khi đặt.',
    expected: 'Trang tra cứu điền sẵn mã; xem được trạng thái, khoảng ngày, số đêm, tổng tiền, tiền cọc.',
    actual: '',
    executionTime: '',
    status: 'PENDING',
    criteria: 'S2-08',
  },
  {
    id: 'TC-05',
    title: 'Xác nhận booking, ghi cọc và email xác nhận',
    role: 'Lễ tân (RECEPTIONIST)',
    roleCode: 'RECEPTIONIST',
    roleBadgeClass: 'sdp-badge--receptionist',
    inputs: 'Danh sách booking → xác nhận booking ở TC-03, ghi cọc chuyển khoản kèm mã tham chiếu.',
    expected: 'Booking đã xác nhận, hạn giữ chỗ gỡ bỏ; Mailpit có email xác nhận có số đêm, giờ nhận/trả phòng.',
    actual: '',
    executionTime: '',
    status: 'PENDING',
    criteria: 'S3-01, S4-04 AC1–AC2',
  },
  {
    id: 'TC-06',
    title: 'Gán phòng và xem sơ đồ phòng',
    role: 'Lễ tân (RECEPTIONIST)',
    roleCode: 'RECEPTIONIST',
    roleBadgeClass: 'sdp-badge--receptionist',
    inputs: 'Gán phòng cho booking ở TC-03; mở Sơ đồ phòng theo ngày.',
    expected: 'Chỉ gợi ý phòng đúng loại còn trống cả khoảng ngày; sơ đồ hiện booking đúng ô ngày.',
    actual: '',
    executionTime: '',
    status: 'PENDING',
    criteria: 'S3-03, S3-10',
  },
  {
    id: 'TC-07',
    title: 'Nhận phòng và xem giấy tờ khách',
    role: 'Lễ tân (RECEPTIONIST)',
    roleCode: 'RECEPTIONIST',
    roleBadgeClass: 'sdp-badge--receptionist',
    inputs: 'Nhận phòng BK-S4-018 (khách đến hôm nay), nhập CCCD 12 số; mở chi tiết booking → Xem đầy đủ.',
    expected: 'Booking đang ở, phòng đang ở; giấy tờ hiện ********xxxx, bấm Xem đầy đủ hiện đủ số và có dòng nhật ký.',
    actual: '',
    executionTime: '',
    status: 'PENDING',
    criteria: 'S3-07, NFR bảo vệ dữ liệu cá nhân, S1-05',
  },
  {
    id: 'TC-08',
    title: 'Thêm phụ thu giữa kỳ lưu trú',
    role: 'Lễ tân (RECEPTIONIST)',
    roleCode: 'RECEPTIONIST',
    roleBadgeClass: 'sdp-badge--receptionist',
    inputs: 'Chi tiết BK-S4-017 → Thêm phụ thu thêm giường, 1 giường, 1 đêm.',
    expected: 'Dòng phụ thu hiện trong chi tiết booking và trong bảng tính trả phòng.',
    actual: '',
    executionTime: '',
    status: 'PENDING',
    criteria: 'S4-02',
  },
  {
    id: 'TC-09',
    title: 'Trả phòng có phụ thu trả muộn',
    role: 'Lễ tân (RECEPTIONIST)',
    roleCode: 'RECEPTIONIST',
    roleBadgeClass: 'sdp-badge--receptionist',
    inputs: 'Sau 12:00: trả phòng BK-S4-016, thu phần còn lại bằng tiền mặt.',
    expected: 'Bảng tính có tiền phòng, phụ thu trả muộn, trừ cọc; booking đã trả phòng, phòng chuyển trống bẩn.',
    actual: '',
    executionTime: '',
    status: 'PENDING',
    criteria: 'S4-01',
  },
  {
    id: 'TC-10',
    title: 'Xuất hoá đơn PDF',
    role: 'Lễ tân (RECEPTIONIST)',
    roleCode: 'RECEPTIONIST',
    roleBadgeClass: 'sdp-badge--receptionist',
    inputs: 'Xuất hoá đơn PDF của BK-S4-016.',
    expected: 'Số hoá đơn dạng năm-số thứ tự, có dòng tổng bằng chữ, in vừa khổ A4.',
    actual: '',
    executionTime: '',
    status: 'PENDING',
    criteria: 'S4-03',
  },
  {
    id: 'TC-11',
    title: 'Dọn phòng và kiểm tra phân quyền',
    role: 'Buồng phòng (HOUSEKEEPING)',
    roleCode: 'HOUSEKEEPING',
    roleBadgeClass: 'sdp-badge--housekeeping',
    inputs: 'Trên điện thoại: báo phòng vừa trả ở TC-09 đã sạch; gõ thẳng đường dẫn /users.',
    expected: 'Phòng về trống sạch, ghi người thao tác; đường dẫn quản trị bị chặn.',
    actual: '',
    executionTime: '',
    status: 'PENDING',
    criteria: 'S3-09, S1-04, S4-08',
  },
  {
    id: 'TC-12',
    title: 'Báo cáo doanh thu và công suất',
    role: 'Chủ homestay (OWNER)',
    roleCode: 'OWNER',
    roleBadgeClass: 'sdp-badge--owner',
    inputs: 'Doanh thu hôm nay; công suất 30 ngày; tải CSV.',
    expected: 'Doanh thu có BK-S4-016, tách tiền phòng và phụ thu; CSV mở đúng tiếng Việt trên Excel.',
    actual: '',
    executionTime: '',
    status: 'PENDING',
    criteria: 'S4-06, S4-07',
  },
  {
    id: 'TC-13',
    title: 'Lễ tân chỉ xem doanh thu hôm nay',
    role: 'Lễ tân (RECEPTIONIST)',
    roleCode: 'RECEPTIONIST',
    roleBadgeClass: 'sdp-badge--receptionist',
    inputs: 'Mở Doanh thu, thử đổi khoảng ngày sang cả tháng.',
    expected: 'Chỉ xem được ngày hiện tại, không đổi được sang toàn kỳ.',
    actual: '',
    executionTime: '',
    status: 'PENDING',
    criteria: 'S4-07 AC4',
  },
  {
    id: 'TC-14',
    title: 'Huỷ booking, hoàn cọc và email huỷ',
    role: 'Lễ tân (RECEPTIONIST)',
    roleCode: 'RECEPTIONIST',
    roleBadgeClass: 'sdp-badge--receptionist',
    inputs: 'Huỷ một booking đã xác nhận còn xa ngày nhận phòng, chọn lý do.',
    expected: 'Tỷ lệ và số tiền hoàn tự tính theo mốc; Mailpit có email huỷ nêu lý do và số tiền hoàn.',
    actual: '',
    executionTime: '',
    status: 'PENDING',
    criteria: 'S3-05, S4-05',
  },
  {
    id: 'TC-15',
    title: 'Nhật ký, lịch sử email và ma trận quyền',
    role: 'Quản trị (ADMIN)',
    roleCode: 'ADMIN',
    roleBadgeClass: 'sdp-badge--admin',
    inputs: 'Mở Nhật ký, Email đã gửi, Ma trận quyền API.',
    expected: 'Nhật ký có đăng nhập, xác nhận booking, xem giấy tờ khách; lịch sử email đủ các thư ở trên; ma trận không còn sai lệch.',
    actual: '',
    executionTime: '',
    status: 'PENDING',
    criteria: 'S1-05, S4-04 AC5, S4-08',
  },
  {
    id: 'TC-16',
    title: 'Nạp lại lần 2 và chạy lại',
    role: 'Chủ homestay (OWNER)',
    roleCode: 'OWNER',
    roleBadgeClass: 'sdp-badge--owner',
    inputs: 'Nạp lại dữ liệu mẫu khi đã có hoá đơn, rồi lặp lại TC-07 và TC-09.',
    expected: 'Nạp lại không lỗi; nhận phòng, trả phòng chạy lại được (tính tái lập).',
    actual: '',
    executionTime: '',
    status: 'PENDING',
    criteria: 'S4-09 AC2, tính tái lập',
  },
]

export const STAGING_SESSION_METADATA: StagingAcceptanceSession = {
  documentCode: 'BB-NT-S4-09-STAGING',
  project: 'Hệ thống đặt phòng HomeStay (TTCS K12C3 N4)',
  epic: '#S4-09: Dữ liệu mẫu và chạy thử kịch bản nghiệm thu',
  story: 'S4-09 AC4: kịch bản nghiệm thu được chạy thử ít nhất một lần trên môi trường staging và ghi lại kết quả',
  environment: 'Staging Docker Compose trên máy nhóm (S4-10), DEMO_MODE=true',
  serverDetails: 'PostgreSQL 15, Java 17 Temurin, Spring Boot 4.1.1, React 18 + Vite, nginx, Mailpit',
  executionDate: 'Chưa chạy (ghi ngày khi chạy thử)',
  executionTimeRange: 'Ghi giờ bắt đầu – kết thúc khi chạy',
  tester: 'Nhóm phát triển (TTCS K12C3 Nhóm 4)',
  poSignoff: 'Product Owner / Giảng viên hướng dẫn',
  totalTests: STAGING_TEST_CASES.length,
  passedTests: 0,
  failedTests: 0,
  repeatabilityStatus: 'Kiểm tra ở TC-16: nạp lại dữ liệu mẫu khi đã có hoá đơn rồi chạy lại nhận phòng, trả phòng',
  overallStatus: 'IN_REVIEW',
}

/** Lỗi đã phát hiện và sửa khi chạy thử trên máy local (16/10/2026), trước khi có staging. */
export const STAGING_DEFECTS: StagingDefect[] = [
  {
    id: 'DEF-01',
    description: 'Tài khoản demo đăng nhập báo sai mật khẩu khi nạp mẫu',
    severity: 'Trung bình',
    rootCause: 'Hash mật khẩu demo ban đầu chưa khớp cost factor BCrypt của hệ thống',
    resolution: 'Đã cập nhật hash chuẩn $2a$10$... trong DemoAccountInitializer và SampleDataService.',
    status: 'Đã giải quyết',
  },
  {
    id: 'DEF-02',
    description: 'Lưới lịch phòng 14 ngày bị lệch 1 ngày khi người dùng ở timezone khác UTC',
    severity: 'Thấp',
    rootCause: 'Hàm tạo dải ngày dùng toISOString() chuyển ngày về UTC',
    resolution: 'Đã chuẩn hóa chuỗi ngày dạng local YYYY-MM-DD trong roomCalendarRanges.ts.',
    status: 'Đã giải quyết',
  },
  {
    id: 'DEF-03',
    description: 'Thiếu bậc hoàn tiền khi hủy phòng Studio Người Lớn',
    severity: 'Thấp',
    rootCause: 'Bảng room_type_cancellation_tiers chưa được seed cho phòng Studio',
    resolution: 'Đã bổ sung chính sách hủy 48h hoàn 80% cho Studio trong kịch bản nạp mẫu.',
    status: 'Đã giải quyết',
  },
]

function statusLabel(status: AcceptanceTestCase['status']): string {
  if (status === 'PASS') return 'ĐẠT'
  if (status === 'FAIL') return 'KHÔNG ĐẠT'
  return 'CHƯA CHẠY'
}

function cell(text: string): string {
  return text.replace(/\n/g, ' ').replace(/\|/g, '\\|')
}

/**
 * Xuất biên bản dạng Markdown từ kết quả đang ghi trên trang, để dán vào docs/staging-acceptance-test-report.md.
 */
export function generateAcceptanceReportMarkdown(
  testCases: AcceptanceTestCase[] = STAGING_TEST_CASES,
  session: StagingAcceptanceSession = STAGING_SESSION_METADATA
): string {
  const passed = testCases.filter((tc) => tc.status === 'PASS').length
  const failed = testCases.filter((tc) => tc.status === 'FAIL').length
  const total = testCases.length

  const rows = testCases
    .map(
      (tc) =>
        `| **${tc.id}** | ${cell(tc.title)} | ${tc.role} | ${cell(tc.inputs)} | ${cell(tc.expected)} | ${cell(tc.actual) || '(chưa ghi)'} | ${tc.executionTime || '—'} | **${statusLabel(tc.status)}** |`
    )
    .join('\n')

  const defectRows = STAGING_DEFECTS.map(
    (d) =>
      `| **${d.id}** | ${d.description} | ${d.severity} | ${d.rootCause} | ${d.resolution} | **${d.status}** |`
  ).join('\n')

  return `# BIÊN BẢN CHẠY THỬ KỊCH BẢN NGHIỆM THU TRÊN STAGING (S4-09 AC4)
**Mã tài liệu:** ${session.documentCode}
**Dự án:** ${session.project}
**Hạng mục:** ${session.epic}
**Yêu cầu:** ${session.story}
**Môi trường:** ${session.environment} (${session.serverDetails})
**Thời gian thực hiện:** ${session.executionDate}, ${session.executionTimeRange}
**Người thực hiện:** ${session.tester}
**Đại diện PO xác nhận:** ${session.poSignoff}
**Kết quả tổng quát:** ${passed}/${total} bước ĐẠT, ${failed} bước KHÔNG ĐẠT, ${total - passed - failed} bước chưa chạy

---

## 1. KẾT QUẢ TỪNG BƯỚC

| Mã | Bước | Vai trò | Dữ liệu / thao tác | Kết quả mong đợi | Kết quả thực tế | Thời điểm | Kết quả |
|---|---|---|---|---|---|---|---|
${rows}

---

## 2. TÍNH TÁI LẬP
${session.repeatabilityStatus}

---

## 3. LỖI ĐÃ PHÁT HIỆN VÀ SỬA (TRƯỚC KHI CÓ STAGING)
| Mã lỗi | Mô tả | Mức độ | Nguyên nhân | Cách khắc phục | Tình trạng |
|---|---|---|---|---|---|
${defectRows}

---

**XÁC NHẬN CỦA PRODUCT OWNER:**
- Trạng thái phê duyệt: *Chờ Product Owner phê duyệt*
- Ngày ghi nhận: ${session.executionDate}
`
}
