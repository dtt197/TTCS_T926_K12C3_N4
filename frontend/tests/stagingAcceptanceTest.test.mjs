import assert from 'node:assert/strict'
import test from 'node:test'
import {
  STAGING_SESSION_METADATA,
  STAGING_TEST_CASES,
  STAGING_DEFECTS,
  generateAcceptanceReportMarkdown,
} from '../src/pages/stagingAcceptanceData.ts'

test('S4-09 AC4: phiên chạy thử ghi rõ môi trường staging Docker', () => {
  assert.equal(STAGING_SESSION_METADATA.documentCode, 'BB-NT-S4-09-STAGING')
  assert.equal(STAGING_SESSION_METADATA.overallStatus, 'IN_REVIEW')
  assert.equal(STAGING_SESSION_METADATA.totalTests, STAGING_TEST_CASES.length)
  assert.ok(STAGING_SESSION_METADATA.environment.includes('Staging'))
  assert.ok(STAGING_SESSION_METADATA.environment.includes('Docker'))
  assert.ok(!STAGING_SESSION_METADATA.environment.includes('local'))
  assert.ok(STAGING_SESSION_METADATA.serverDetails.includes('PostgreSQL 15'))
  assert.ok(STAGING_SESSION_METADATA.serverDetails.includes('Spring Boot 4.1.1'))
})

test('S4-09 AC4: kịch bản có 16 bước khớp biên bản docs/staging-acceptance-test-report.md', () => {
  const expectedIds = Array.from({ length: 16 }, (_, index) => `TC-${String(index + 1).padStart(2, '0')}`)
  assert.deepEqual(STAGING_TEST_CASES.map((tc) => tc.id), expectedIds)
})

test('S4-09 AC4: mỗi bước có thao tác, kết quả mong đợi, tiêu chí; kết quả thực tế để trống chờ chạy thật', () => {
  for (const tc of STAGING_TEST_CASES) {
    assert.ok(tc.title.length > 0, `${tc.id} phải có tên bước`)
    assert.ok(tc.inputs.length > 0, `${tc.id} phải có thao tác`)
    assert.ok(tc.expected.length > 0, `${tc.id} phải có kết quả mong đợi`)
    assert.ok(tc.criteria.length > 0, `${tc.id} phải ghi story/AC kiểm tra`)
    assert.equal(tc.status, 'PENDING', `${tc.id} chưa chạy thì không được đánh dấu đạt sẵn`)
    assert.equal(tc.actual, '', `${tc.id} không được điền sẵn kết quả thực tế`)
  }
})

test('S4-09 AC4: kịch bản bao phủ cả 5 vai trò', () => {
  const roles = new Set(STAGING_TEST_CASES.map((tc) => tc.roleCode))
  for (const role of ['PUBLIC', 'RECEPTIONIST', 'HOUSEKEEPING', 'OWNER', 'ADMIN']) {
    assert.ok(roles.has(role), `Phải có vai trò ${role}`)
  }
})

test('S4-09: nhật ký lỗi đã sửa có đủ cách khắc phục', () => {
  assert.ok(STAGING_DEFECTS.length >= 3)
  for (const defect of STAGING_DEFECTS) {
    assert.ok(defect.id.startsWith('DEF-'), 'Mã lỗi phải bắt đầu bằng DEF-')
    assert.equal(defect.status, 'Đã giải quyết')
    assert.ok(defect.resolution.length > 0)
  }
})

test('S4-09 AC4: biên bản Markdown xuất đúng kết quả đã ghi', () => {
  const blank = generateAcceptanceReportMarkdown(STAGING_TEST_CASES, STAGING_SESSION_METADATA)
  assert.ok(blank.includes('BB-NT-S4-09-STAGING'))
  assert.ok(blank.includes('0/16 bước ĐẠT'))
  assert.ok(blank.includes('(chưa ghi)'))
  assert.ok(blank.includes('TC-16'))
  assert.ok(blank.includes('Chờ Product Owner phê duyệt'))

  const recorded = STAGING_TEST_CASES.map((tc, index) => index === 0
    ? { ...tc, status: 'PASS', actual: 'Nạp xong 4 loại phòng | 20 phòng, 30 booking trong 1,9 giây', executionTime: '13:05:10' }
    : tc)
  const md = generateAcceptanceReportMarkdown(recorded, STAGING_SESSION_METADATA)
  assert.ok(md.includes('1/16 bước ĐẠT'))
  assert.ok(md.includes('Nạp xong 4 loại phòng \\| 20 phòng'))
  assert.ok(md.includes('13:05:10'))
})
