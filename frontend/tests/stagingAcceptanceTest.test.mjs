import assert from 'node:assert/strict'
import test from 'node:test'
import {
  STAGING_SESSION_METADATA,
  STAGING_TEST_CASES,
  STAGING_DEFECTS,
  generateAcceptanceReportMarkdown,
} from '../src/pages/stagingAcceptanceData.ts'

test('SCRUM-140 / S4-09: Biên bản nghiệm thu có đầy đủ thông tin phiên Staging', () => {
  assert.equal(STAGING_SESSION_METADATA.documentCode, 'BB-NT-S4-09-STAGING')
  assert.equal(STAGING_SESSION_METADATA.overallStatus, 'IN_REVIEW')
  assert.equal(STAGING_SESSION_METADATA.totalTests, 8)
  assert.equal(STAGING_SESSION_METADATA.passedTests, 8)
  assert.equal(STAGING_SESSION_METADATA.failedTests, 0)
  assert.ok(STAGING_SESSION_METADATA.environment.includes('Staging'))
  assert.ok(STAGING_SESSION_METADATA.environment.includes('Docker'))
  assert.ok(STAGING_SESSION_METADATA.serverDetails.includes('PostgreSQL 15'))
  assert.ok(STAGING_SESSION_METADATA.serverDetails.includes('Spring Boot 4.1.1'))
})

test('SCRUM-140 / S4-09: Chứa đầy đủ 8 kịch bản nghiệm thu trọn luồng', () => {
  assert.equal(STAGING_TEST_CASES.length, 8)

  const expectedIds = ['TC-01', 'TC-02', 'TC-03', 'TC-04', 'TC-05', 'TC-06', 'TC-07', 'TC-08']
  const actualIds = STAGING_TEST_CASES.map((tc) => tc.id)
  assert.deepEqual(actualIds, expectedIds)
})

test('SCRUM-140 / S4-09: Mỗi test case đều có đầy đủ inputs, expected, actual và tiêu chí đạt', () => {
  for (const tc of STAGING_TEST_CASES) {
    assert.ok(tc.title.length > 0, `${tc.id} phải có title`)
    assert.ok(tc.inputs.length > 0, `${tc.id} phải có inputs`)
    assert.ok(tc.expected.length > 0, `${tc.id} phải có expected outcome`)
    assert.ok(tc.actual.length > 0, `${tc.id} phải có actual result trên Staging`)
    assert.ok(tc.criteria.length > 0, `${tc.id} phải có criteria`)
    assert.equal(tc.status, 'PASS', `${tc.id} phải có trạng thái PASS trên Staging`)
  }
})

test('SCRUM-140 / S4-09: Kịch bản bao phủ đầy đủ các vai trò vận hành', () => {
  const roles = new Set(STAGING_TEST_CASES.map((tc) => tc.roleCode))
  assert.ok(roles.has('OWNER'), 'Phải có vai trò OWNER (Nạp mẫu & Báo cáo)')
  assert.ok(roles.has('PUBLIC'), 'Phải có vai trò PUBLIC (Tra cứu & Đặt phòng)')
  assert.ok(roles.has('RECEPTIONIST'), 'Phải có vai trò RECEPTIONIST (Lịch phòng, Check-in, Phụ thu, Check-out)')
  assert.ok(roles.has('HOUSEKEEPING'), 'Phải có vai trò HOUSEKEEPING (Dọn dẹp & Chuyển phòng sạch)')
})

test('SCRUM-140 / S4-09: Nhật ký lỗi (Defect Log) có đầy đủ phương án khắc phục đã giải quyết', () => {
  assert.ok(STAGING_DEFECTS.length >= 3)
  for (const defect of STAGING_DEFECTS) {
    assert.ok(defect.id.startsWith('DEF-'), 'Mã defect phải bắt đầu bằng DEF-')
    assert.equal(defect.status, 'Đã giải quyết')
    assert.ok(defect.resolution.length > 0)
  }
})

test('SCRUM-140 / S4-09: Xuất báo cáo Markdown có đầy đủ bảng biểu và chữ ký PO', () => {
  const md = generateAcceptanceReportMarkdown(STAGING_TEST_CASES, STAGING_SESSION_METADATA)
  assert.ok(md.includes('BB-NT-S4-09-STAGING'))
  assert.ok(md.includes('8/8 Kịch bản ĐẠT'))
  assert.ok(md.includes('TC-01'))
  assert.ok(md.includes('TC-08'))
  assert.ok(md.includes('PRODUCT OWNER'))
  assert.ok(md.includes('Chờ Product Owner phê duyệt'))
})
