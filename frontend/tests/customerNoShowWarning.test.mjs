import test from 'node:test'
import assert from 'node:assert/strict'
import { evaluateNoShowBadge } from '../src/pages/customerNoShowHelper.ts'

test('SCRUM-150 / S4-04 Lát 2: Hiển thị cảnh báo số lần không đến khi tạo lịch hẹn mới', async (t) => {
  await t.test('Kịch bản 1: SĐT khách không có lịch sử bỏ hẹn (0 lần) -> Không hiển thị thẻ cảnh báo', () => {
    const badge = evaluateNoShowBadge(0)

    assert.equal(badge.hasWarning, false)
    assert.equal(badge.level, null)
    assert.equal(badge.message, null)
    assert.equal(badge.badgeClassName, '')
  })

  await t.test('Kịch bản 2: SĐT khách đã bỏ hẹn 1 lần -> Hiển thị badge màu vàng (is-warning)', () => {
    const badge = evaluateNoShowBadge(1)

    assert.equal(badge.hasWarning, true)
    assert.equal(badge.level, 'warning')
    assert.ok(badge.badgeClassName.includes('is-warning'))
    assert.equal(badge.message, '⚠️ Khách hàng này đã bỏ hẹn 1 lần')
    assert.ok(badge.description?.includes('lịch sử'))
  })

  await t.test('Kịch bản 3: SĐT khách đã bỏ hẹn 2 lần -> Hiển thị badge cảnh báo màu đỏ (is-danger)', () => {
    const badge = evaluateNoShowBadge(2)

    assert.equal(badge.hasWarning, true)
    assert.equal(badge.level, 'danger')
    assert.ok(badge.badgeClassName.includes('is-danger'))
    assert.equal(badge.message, '⚠️ Khách hàng này đã bỏ hẹn 2 lần')
    assert.ok(badge.description?.includes('yêu cầu thanh toán tiền cọc'))
  })

  await t.test('Kịch bản 3b: SĐT khách đã bỏ hẹn nhiều lần (ví dụ 4 lần) -> Hiển thị đúng số lần và badge đỏ', () => {
    const badge = evaluateNoShowBadge(4)

    assert.equal(badge.hasWarning, true)
    assert.equal(badge.level, 'danger')
    assert.ok(badge.badgeClassName.includes('is-danger'))
    assert.equal(badge.message, '⚠️ Khách hàng này đã bỏ hẹn 4 lần')
  })

  await t.test('Kịch bản 4: Giả lập quy trình Lễ tân tạo lịch hẹn mới và nhập SĐT', () => {
    // Giả lập dữ liệu trả về từ API tra cứu SĐT X
    const apiLookupResult = {
      phone: '0987654321',
      fullName: 'Nguyen Van A',
      noShowCount: 2,
      hasWarning: true,
      warningMessage: '⚠️ Khách hàng này đã bỏ hẹn 2 lần',
    }

    // Khi Lễ tân nhập SĐT 0987654321
    assert.equal(apiLookupResult.phone, '0987654321')
    assert.equal(apiLookupResult.hasWarning, true)

    // Form giao diện tính toán badge cảnh báo
    const badge = evaluateNoShowBadge(apiLookupResult.noShowCount)
    assert.equal(badge.hasWarning, true)
    assert.equal(badge.level, 'danger')
    assert.equal(badge.message, '⚠️ Khách hàng này đã bỏ hẹn 2 lần')
  })
})
