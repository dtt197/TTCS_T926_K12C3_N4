import type { BookingListItem } from '../types/booking'

export type BookingAction = 'confirm' | 'assign-room' | 'change-room' | 'edit' | 'cancel' | 'no-show' | 'undo-no-show'

export function isNoShowEligible(
  booking: Pick<BookingListItem, 'status' | 'checkInDate'>,
  nowInput?: Date | string,
  checkInTime = '14:00',
): { eligible: boolean; reason?: string } {
  if (booking.status === 'DA_NHAN_PHONG' || booking.status === 'DA_TRA_PHONG') {
    return { eligible: false, reason: 'Lịch hẹn đã check-in, không thể đánh dấu khách không đến' }
  }
  if (booking.status !== 'CHO_XAC_NHAN' && booking.status !== 'DA_XAC_NHAN') {
    return { eligible: false, reason: 'Trạng thái không hợp lệ để đánh dấu khách không đến' }
  }
  const now = nowInput instanceof Date ? nowInput : nowInput ? new Date(nowInput) : new Date()
  const [timeH, timeM] = checkInTime.split(':').map(Number)
  const scheduledTime = new Date(`${booking.checkInDate}T${String(timeH).padStart(2, '0')}:${String(timeM).padStart(2, '0')}:00+07:00`)
  const eligibleAfter = new Date(scheduledTime.getTime() + 30 * 60 * 1000)

  if (now.getTime() < eligibleAfter.getTime()) {
    return { eligible: false, reason: 'Chưa đủ điều kiện: Chỉ được đánh dấu sau giờ hẹn 30 phút' }
  }
  return { eligible: true }
}

/** S4-04 (SCRUM-151): Kiểm tra điều kiện hoàn tác thao tác không đến trong 15 phút */
export function isUndoNoShowEligible(
  markedAt: Date | string,
  nowInput?: Date | string | number,
  undoWindowMinutesInput: number | Date | string = 15,
): { eligible: boolean; remainingSeconds: number; reason?: string } {
  let now: Date
  let windowMinutes = 15

  if (typeof nowInput === 'number') {
    windowMinutes = nowInput
    if (undoWindowMinutesInput instanceof Date) {
      now = undoWindowMinutesInput
    } else if (typeof undoWindowMinutesInput === 'string') {
      now = new Date(undoWindowMinutesInput)
    } else {
      now = new Date()
    }
  } else if (nowInput instanceof Date) {
    now = nowInput
    if (typeof undoWindowMinutesInput === 'number') {
      windowMinutes = undoWindowMinutesInput
    }
  } else if (typeof nowInput === 'string') {
    now = new Date(nowInput)
    if (typeof undoWindowMinutesInput === 'number') {
      windowMinutes = undoWindowMinutesInput
    }
  } else {
    now = new Date()
    if (typeof undoWindowMinutesInput === 'number') {
      windowMinutes = undoWindowMinutesInput
    }
  }

  const markedDate = markedAt instanceof Date ? markedAt : new Date(markedAt)
  const expiresAt = new Date(markedDate.getTime() + windowMinutes * 60 * 1000)
  const diffMs = expiresAt.getTime() - now.getTime()
  const remainingSeconds = Math.max(0, Math.floor(diffMs / 1000))

  if (remainingSeconds <= 0) {
    return { eligible: false, remainingSeconds: 0, reason: `Đã hết thời hạn ${windowMinutes} phút để hoàn tác` }
  }
  return { eligible: true, remainingSeconds }
}

export function formatRemainingTime(seconds: number): string {
  const safeSeconds = Math.max(0, Math.floor(seconds))
  const mins = Math.floor(safeSeconds / 60)
  const secs = safeSeconds % 60
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
}

export function bookingActionsFor(
  booking: BookingListItem,
  permissions: { manage: boolean; cancel: boolean; noShow?: boolean },
  canChangeRoom: boolean,
): BookingAction[] {
  const actions: BookingAction[] = []
  if (permissions.manage && booking.status === 'CHO_XAC_NHAN' && !booking.holdExpired) actions.push('confirm')
  if (permissions.manage && booking.status === 'DA_XAC_NHAN' && !booking.roomConfirmedAt) actions.push('assign-room')
  if (canChangeRoom) actions.push('change-room')
  if (permissions.manage && (booking.status === 'CHO_XAC_NHAN' || booking.status === 'DA_XAC_NHAN')) actions.push('edit')
  if (permissions.cancel && (booking.status === 'CHO_XAC_NHAN' || booking.status === 'DA_XAC_NHAN')) actions.push('cancel')
  if (permissions.noShow && (booking.status === 'CHO_XAC_NHAN' || booking.status === 'DA_XAC_NHAN')) actions.push('no-show')
  if (permissions.noShow && booking.status === 'NO_SHOW') actions.push('undo-no-show')
  return actions
}
