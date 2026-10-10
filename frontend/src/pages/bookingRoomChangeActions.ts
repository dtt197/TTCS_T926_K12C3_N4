import type { BookingListItem, BookingRoomChangeHistory } from '../types/booking'
import type { AssignableRoom } from '../types/booking'

type RoomChangeActions = {
  changeBookingRoom: (bookingId: number, roomId: number, reason: string) => Promise<BookingListItem>
  reloadBookingData: () => Promise<void>
  getBookingRoomChangeHistory: (bookingId: number) => Promise<BookingRoomChangeHistory[]>
  setRoomChangeHistory: (history: BookingRoomChangeHistory[]) => void
  setRoomError: (error: string | null) => void
  setRoomSaving: (saving: boolean) => void
  setChangeRoomBooking: (booking: BookingListItem | null) => void
  setEditSuccess: (message: string | null) => void
}

export async function submitRoomChange(
  booking: BookingListItem | null,
  roomId: number | null,
  rawReason: string,
  actions: RoomChangeActions,
) {
  if (!booking) return
  const reason = rawReason.trim()
  if (!reason) {
    actions.setRoomError('Vui lòng nhập lý do đổi phòng.')
    return
  }
  if (roomId === null) {
    actions.setRoomError('Vui lòng chọn phòng mới.')
    return
  }
  actions.setRoomSaving(true)
  actions.setRoomError(null)
  try {
    await actions.changeBookingRoom(booking.id, roomId, reason)
    await actions.reloadBookingData()
    actions.setRoomChangeHistory(await actions.getBookingRoomChangeHistory(booking.id))
    actions.setChangeRoomBooking(null)
    actions.setEditSuccess('Đã đổi phòng và lưu lịch sử.')
  } catch (err) {
    actions.setRoomError(err instanceof Error ? err.message : 'Không đổi được phòng.')
  } finally {
    actions.setRoomSaving(false)
  }
}

export async function loadRoomChangeHistory(
  booking: BookingListItem,
  getHistory: (bookingId: number) => Promise<BookingRoomChangeHistory[]>,
  setHistory: (history: BookingRoomChangeHistory[]) => void,
) {
  setHistory(await getHistory(booking.id))
}

export type { AssignableRoom }

export function canChangeBookingRoom(role: string, booking: BookingListItem, today: string) {
  return role === 'RECEPTIONIST' && (booking.status === 'DA_NHAN_PHONG'
    || (booking.status === 'DA_XAC_NHAN' && !!booking.roomConfirmedAt && booking.checkInDate >= today))
}

export function roomChangeOptions(booking: BookingListItem, rooms: AssignableRoom[]) {
  return rooms.filter(room => room.roomNumber !== booking.roomNumber
    && (booking.status !== 'DA_NHAN_PHONG' || room.status === 'TRONG_SACH'))
}
