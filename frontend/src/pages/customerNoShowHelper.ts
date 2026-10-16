export type NoShowBadgeLevel = 'warning' | 'danger' | null

export interface NoShowBadgeInfo {
  hasWarning: boolean
  level: NoShowBadgeLevel
  badgeClassName: string
  message: string | null
  description: string | null
}

/**
 * S4-04 (SCRUM-150): Xác định mức cảnh báo và nội dung badge dựa trên số lần bỏ hẹn.
 * - count == 1: mức vàng (warning)
 * - count >= 2: mức đỏ (danger)
 * - count == 0: không cảnh báo
 */
export function evaluateNoShowBadge(count: number): NoShowBadgeInfo {
  if (!count || count <= 0) {
    return {
      hasWarning: false,
      level: null,
      badgeClassName: '',
      message: null,
      description: null,
    }
  }

  if (count === 1) {
    return {
      hasWarning: true,
      level: 'warning',
      badgeClassName: 'no-show-warning-badge is-warning',
      message: '⚠️ Khách hàng này đã bỏ hẹn 1 lần',
      description: 'Khách hàng có lịch sử từng không đến theo lịch hẹn.',
    }
  }

  return {
    hasWarning: true,
    level: 'danger',
    badgeClassName: 'no-show-warning-badge is-danger',
    message: `⚠️ Khách hàng này đã bỏ hẹn ${count} lần`,
    description: 'Lưu ý: Khách có tỷ lệ bỏ hẹn cao, nên yêu cầu thanh toán tiền cọc.',
  }
}
