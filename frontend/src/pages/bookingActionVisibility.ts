import type { BookingListItem } from '../types/booking'

export type BookingAction = 'confirm' | 'assign-room' | 'change-room' | 'edit' | 'cancel' | 'no-show'

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
  return actions
}
