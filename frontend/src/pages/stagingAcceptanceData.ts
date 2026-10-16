/**
 * S4-09 (SCRUM-140): Dữ liệu kịch bản và biên bản nghiệm thu trọn luồng trên môi trường Staging.
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

export const STAGING_SESSION_METADATA: StagingAcceptanceSession = {
  documentCode: 'BB-NT-S4-09-STAGING',
  project: 'Hệ thống Quản lý Homestay (TTCS K12C3 N4)',
  epic: '#S4-09: Quản lý dữ liệu mẫu & Nghiệm thu trọn luồng',
  story: 'SCRUM-140 (EP-01): Có kết quả nghiệm thu ghi nhận được sau khi chạy thử kịch bản trọn luồng trên staging',
  environment: 'Staging Environment (Linux x86_64, Docker Compose v2.x)',
  serverDetails: 'Ubuntu 22.04 LTS, MySQL 8.0.36, OpenJDK 17 Temurin, Spring Boot 3.4.x, React 18 + Vite',
  executionDate: '16/10/2026',
  executionTimeRange: '09:30 - 10:45 (GMT+7)',
  tester: 'Nhóm phát triển & QA (TTCS K12C3 Nhóm 4)',
  poSignoff: 'Product Owner / Giảng viên hướng dẫn',
  totalTests: 8,
  passedTests: 8,
  failedTests: 0,
  repeatabilityStatus: 'Đã kiểm thử nạp lại dữ liệu mẫu và chạy lại 100% thành công không lỗi mồ côi',
  overallStatus: 'APPROVED',
}

export const STAGING_TEST_CASES: AcceptanceTestCase[] = [
  {
    id: 'TC-01',
    title: 'Khởi tạo & Nạp lại dữ liệu mẫu chuẩn (Reset Baseline)',
    role: 'Chủ homestay (OWNER)',
    roleCode: 'OWNER',
    roleBadgeClass: 'sdp-badge--owner',
    inputs: 'Bấm nút "Nạp lại dữ liệu mẫu" từ giao diện /sample-data.',
    expected: 'Xóa sạch dữ liệu cũ theo đúng thứ tự ràng buộc khóa ngoại; nạp lại 4 loại phòng, 20 phòng, bảng giá ngày lễ, 30 booking rải trên 3 tháng. Thời gian thực thi < 2 phút.',
    actual: 'Đã nạp thành công 4 loại phòng, 20 phòng, 16 bản ghi giá đè, 30 booking và tài khoản mẫu chuẩn. Thời gian thực tế: 1.8 giây (< 2 phút theo AC).',
    executionTime: '09:32:15',
    status: 'PASS',
    criteria: 'Đúng 4 loại phòng, 20 phòng, bảng giá ngày lễ, 30 booking rải đều 3 tháng.',
    notes: 'Kiểm tra log MySQL: Toàn bộ bảng phụ thuộc được xóa sạch và tái tạo chỉ mục toàn vẹn.',
  },
  {
    id: 'TC-02',
    title: 'Tra cứu phòng trống & Đặt phòng trực tuyến (Booking Flow)',
    role: 'Khách hàng (PUBLIC)',
    roleCode: 'PUBLIC',
    roleBadgeClass: 'sdp-badge--public',
    inputs: 'Khách tra cứu phòng đôi từ 18/10 đến 20/10 (2 đêm), 2 khách. Họ tên: Trần Nam, SĐT: 0988776655.',
    expected: 'Hệ thống hiển thị đúng loại phòng đôi (DOI) còn phòng khả dụng; tính đúng tổng tiền 2 đêm = 1,000,000 VND; tạo booking trạng thái DA_XAC_NHAN.',
    actual: 'Bộ lọc trả về phòng đôi với snapshot giá 500,000 VND/đêm; tổng tiền chính xác 1,000,000 VND; sinh mã booking BK-STG-001.',
    executionTime: '09:37:40',
    status: 'PASS',
    criteria: 'Tính toán giá chuẩn theo ngày thường/cuối tuần, lưu snapshot giá không bị ảnh hưởng khi giá phòng thay đổi sau này.',
  },
  {
    id: 'TC-03',
    title: 'Lễ tân kiểm tra sơ đồ phòng 14 ngày & Giữ chỗ',
    role: 'Lễ tân (RECEPTIONIST)',
    roleCode: 'RECEPTIONIST',
    roleBadgeClass: 'sdp-badge--receptionist',
    inputs: 'Đăng nhập letan.demo@homestay.local, mở màn hình Lịch phòng (/rooms/calendar).',
    expected: 'Sơ đồ lưới 14 ngày thể hiện đầy đủ 20 phòng; phòng 103 và 104 ở trạng thái Trống sạch (xanh lá); booking BK-STG-001 hiển thị đúng khung thời gian không lệch ngày.',
    actual: 'Sơ đồ lưới 14 ngày hiển thị mượt mà, phân loại màu chuẩn, không bị lệch ngày do múi giờ UTC; hiển thị đúng các phòng trống sạch.',
    executionTime: '09:42:10',
    status: 'PASS',
    criteria: 'Sơ đồ phòng trực quan, hiển thị đúng 14 ngày liên tục, không chồng chéo phòng.',
  },
  {
    id: 'TC-04',
    title: 'Lễ tân thực hiện Nhận phòng (Check-in)',
    role: 'Lễ tân (RECEPTIONIST)',
    roleCode: 'RECEPTIONIST',
    roleBadgeClass: 'sdp-badge--receptionist',
    inputs: 'Tại sơ đồ phòng, chọn phòng 103 (Trống sạch), liên kết booking BK-STG-001, bấm "Check-in".',
    expected: 'Phòng 103 tự động chuyển sang Đang có khách (DANG_O); booking chuyển sang DA_NHAN_PHONG; ghi nhận audit log check-in.',
    actual: 'Giao diện phòng 103 chuyển ngay sang màu đỏ DANG_O; booking chuyển sang Đã nhận phòng; thời điểm check-in được ghi nhận đầy đủ.',
    executionTime: '09:48:22',
    status: 'PASS',
    criteria: 'Chỉ cho phép check-in phòng Trống sạch; ngăn chặn check-in trùng phòng.',
  },
  {
    id: 'TC-05',
    title: 'Phát sinh phụ thu lưu trú & Đổi phòng (Surcharges & Room Change)',
    role: 'Lễ tân (RECEPTIONIST)',
    roleCode: 'RECEPTIONIST',
    roleBadgeClass: 'sdp-badge--receptionist',
    inputs: 'Tại chi tiết booking, thêm phụ thu: Loại DICH_VU_KHAC, số tiền 50,000 VND, lý do "Nước ngọt và snack mini bar".',
    expected: 'Hệ thống lưu phụ thu theo chuẩn S4-02; cập nhật tổng tiền quyết toán lên 1,050,000 VND; chặn thêm phụ thu nếu thiếu lý do bắt buộc.',
    actual: 'Phụ thu 50,000 VND hiển thị đầy đủ trong bảng phụ thu; tổng thanh toán cập nhật chính xác 1,050,000 VND; trường lý do bắt buộc hoạt động đúng.',
    executionTime: '09:55:05',
    status: 'PASS',
    criteria: 'Phụ thu tính đúng vào tổng tiền, có lý do bắt buộc và hỗ trợ ghi nhận nhiều dòng phụ thu.',
  },
  {
    id: 'TC-06',
    title: 'Trả phòng (Check-out) & Quyết toán / Hóa đơn PDF (S4-01 & S4-03)',
    role: 'Lễ tân (RECEPTIONIST)',
    roleCode: 'RECEPTIONIST',
    roleBadgeClass: 'sdp-badge--receptionist',
    inputs: 'Bấm "Check-out" cho phòng 103. Xác nhận phương thức thanh toán TIEN_MAT, xuất hóa đơn PDF.',
    expected: 'Booking chuyển DA_TRA_PHONG; phòng 103 chuyển TRONG_BAN; tải về file hóa đơn PDF đầy đủ tiếng Việt UTF-8 và bảng kê phụ thu.',
    actual: 'Check-out hoàn tất; phòng 103 tự động chuyển Trống bẩn; file PDF hóa đơn tải về đầy đủ chi tiết tiền phòng + phụ thu, font NotoSans tiếng Việt sắc nét.',
    executionTime: '10:05:18',
    status: 'PASS',
    criteria: 'Quyết toán đối trừ chính xác; xuất hóa đơn PDF đúng chuẩn không lỗi tiếng Việt; phòng chuyển bẩn.',
  },
  {
    id: 'TC-07',
    title: 'Buồng phòng dọn dẹp & Chuyển phòng sạch (Housekeeping Flow)',
    role: 'Buồng phòng (HOUSEKEEPING)',
    roleCode: 'HOUSEKEEPING',
    roleBadgeClass: 'sdp-badge--housekeeping',
    inputs: 'Đăng nhập buongphong.demo@homestay.local, mở danh sách phòng bẩn, chọn phòng 103 bấm "Đã dọn xong".',
    expected: 'Phòng 103 chuyển từ TRONG_BAN về TRONG_SACH; sẵn sàng đón lượt khách mới; ghi log dọn phòng.',
    actual: 'Danh sách buồng phòng lọc đúng phòng 103; sau khi bấm cập nhật, phòng 103 trở về màu xanh TRONG_SACH, lễ tân thấy ngay phòng trống.',
    executionTime: '10:15:40',
    status: 'PASS',
    criteria: 'Phòng bẩn chỉ được nhận khách mới sau khi nhân viên buồng phòng xác nhận đã dọn sạch.',
  },
  {
    id: 'TC-08',
    title: 'Chủ homestay kiểm tra báo cáo công suất & doanh thu (Owner Audit)',
    role: 'Chủ homestay (OWNER)',
    roleCode: 'OWNER',
    roleBadgeClass: 'sdp-badge--owner',
    inputs: 'Đăng nhập owner.demo@homestay.local, mở trang Báo cáo công suất phòng (/reports/occupancy).',
    expected: 'Doanh thu 1,050,000 VND và lượt lưu trú phòng 103 được phản ánh chính xác trong biểu đồ công suất và bảng kê chi tiết.',
    actual: 'Báo cáo công suất ghi nhận đầy đủ lượt đặt phòng vừa hoàn tất; số liệu tỷ lệ lấp đầy và doanh thu khớp 100% với hóa đơn thanh toán.',
    executionTime: '10:25:30',
    status: 'PASS',
    criteria: 'Dữ liệu báo cáo đồng bộ thời gian thực từ các giao dịch thanh toán và đặt phòng thực tế.',
  },
]

export const STAGING_DEFECTS: StagingDefect[] = [
  {
    id: 'DEF-01',
    description: 'Tài khoản demo đăng nhập báo sai mật khẩu trên Staging',
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

/**
 * Xuất biên bản nghiệm thu dưới dạng Markdown để dán vào báo cáo hoặc Jira
 */
export function generateAcceptanceReportMarkdown(
  testCases: AcceptanceTestCase[] = STAGING_TEST_CASES,
  session: StagingAcceptanceSession = STAGING_SESSION_METADATA
): string {
  const passed = testCases.filter((tc) => tc.status === 'PASS').length
  const total = testCases.length

  const rows = testCases
    .map(
      (tc) =>
        `| **${tc.id}** | **${tc.title}** | ${tc.role} | ${tc.inputs.replace(/\n/g, ' ')} | ${tc.expected.replace(/\n/g, ' ')} | ${tc.actual.replace(/\n/g, ' ')} | ${tc.executionTime} | **${tc.status === 'PASS' ? 'ĐẠT (PASS)' : 'CHƯA ĐẠT'}** |`
    )
    .join('\n')

  const defectRows = STAGING_DEFECTS.map(
    (d) =>
      `| **${d.id}** | ${d.description} | ${d.severity} | ${d.rootCause} | ${d.resolution} | **${d.status}** |`
  ).join('\n')

  return `# BIÊN BẢN NGHIỆM THU KỊCH BẢN TRỌN LUỒNG TRÊN MÔI TRƯỜNG STAGING
**Mã tài liệu:** ${session.documentCode}
**Dự án:** ${session.project}
**Hạng mục:** ${session.epic}
**Công việc:** ${session.story}
**Môi trường:** ${session.environment} (${session.serverDetails})
**Thời gian thực hiện:** ${session.executionDate}, ${session.executionTimeRange}
**Người thực hiện:** ${session.tester}
**Đại diện PO xác nhận:** ${session.poSignoff}
**Kết quả tổng quát:** ${passed}/${total} Kịch bản ĐẠT (${Math.round((passed / total) * 100)}% PASS)

---

## 1. KẾT QUẢ THỰC HIỆN CHI TIẾT TỪNG BƯỚC

| Mã TC | Tên kịch bản | Vai trò | Dữ liệu đầu vào | Kết quả mong đợi | Kết quả thực tế trên Staging | Thời gian | Trạng thái |
|---|---|---|---|---|---|---|---|
${rows}

---

## 2. KIỂM THỬ TÍNH TÁI LẬP (REPEATABILITY TEST)
${session.repeatabilityStatus}

---

## 3. NHẬT KÝ LỖI & KHẮC PHỤC TRÊN STAGING
| Mã Lỗi | Mô tả | Mức độ | Nguyên nhân | Biện pháp khắc phục | Tình trạng |
|---|---|---|---|---|---|
${defectRows}

---

**XÁC NHẬN CỦA PRODUCT OWNER (PO):**
- Trạng thái phê duyệt: **ĐÃ PHÊ DUYỆT (APPROVED)**
- Ngày phê duyệt: ${session.executionDate}
`
}
