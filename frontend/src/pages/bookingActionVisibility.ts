import type { BookingListItem } from '../types/booking'

export type BookingAction = 'confirm' | 'assign-room' | 'change-room' | 'edit' | 'cancel'

export function bookingActionsFor(
  booking: BookingListItem,
  permissions: { manage: boolean; cancel: boolean },
  canChangeRoom: boolean,
): BookingAction[] {
  const actions: BookingAction[] = []
  if (permissions.manage && booking.status === 'CHO_XAC_NHAN' && !booking.holdExpired) actions.push('confirm')
  if (permissions.manage && booking.status === 'DA_XAC_NHAN' && !booking.roomConfirmedAt) actions.push('assign-room')
  if (canChangeRoom) actions.push('change-room')
  if (permissions.manage) actions.push('edit')
  if (permissions.cancel && (booking.status === 'CHO_XAC_NHAN' || booking.status === 'DA_XAC_NHAN')) actions.push('cancel')
  return actions
}
