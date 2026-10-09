import type { AssignableRoom, BookingListItem } from '../types/booking'
import { getRoomSelectionOutcome } from './bookingRoomSelection.ts'

type RoomActions = {
  getAvailableRooms: (bookingId: number) => Promise<AssignableRoom[]>
  assignBookingRoom: (bookingId: number, roomId: number) => Promise<BookingListItem>
  setAssignableRooms: (rooms: AssignableRoom[]) => void
  setSelectedRoomId: (roomId: number | null) => void
  setRoomError: (error: string | null) => void
  setRoomLoading: (loading: boolean) => void
  setRoomSaving: (saving: boolean) => void
  setRoomBooking: (booking: BookingListItem | null) => void
  setEditSuccess: (message: string | null) => void
  reloadBookings: () => Promise<void>
}

export async function reloadAssignableRooms(
  booking: BookingListItem | null,
  selectedRoomId: number | null,
  actions: RoomActions,
) {
  if (!booking) return
  actions.setRoomLoading(true)
  actions.setRoomError(null)
  try {
    const rooms = await actions.getAvailableRooms(booking.id)
    actions.setAssignableRooms(rooms)
    // Keep the current selection when the same room is still assignable.
    // Capture it before the request; state may otherwise be cleared by a
    // concurrent UI update while the request is in flight.
    const selectedRoom = rooms.find((room) => room.id === selectedRoomId)
    if (selectedRoom) {
      actions.setSelectedRoomId(selectedRoom.id)
    } else if (selectedRoomId === null && rooms.length === 1) {
      actions.setSelectedRoomId(rooms[0].id)
    } else {
      actions.setSelectedRoomId(null)
      if (selectedRoomId !== null) {
        actions.setRoomError('Phòng đã chọn không còn khả dụng. Vui lòng chọn phòng khác trong danh sách.')
      }
    }
    if (rooms.length === 0) actions.setRoomError('Hiện không còn phòng phù hợp trong toàn bộ kỳ lưu trú. Vui lòng kiểm tra lại ngày hoặc tải lại sau.')
  } catch (err) {
    actions.setRoomError(err instanceof Error ? err.message : 'Không tải được danh sách phòng khả dụng.')
  } finally {
    actions.setRoomLoading(false)
  }
}

export async function confirmRoomSelection(
  booking: BookingListItem | null,
  selectedRoomId: number | null,
  rooms: AssignableRoom[],
  actions: RoomActions,
) {
  if (!booking || selectedRoomId === null) return
  const outcome = getRoomSelectionOutcome(booking.roomNumber, selectedRoomId, rooms)
  if (outcome.type === 'invalid-selection') {
    actions.setRoomError('Phòng đã chọn không còn trong danh sách phòng phù hợp. Hãy tải lại danh sách phòng.')
    return
  }
  actions.setRoomSaving(true)
  actions.setRoomError(null)
  try {
    const updated = await actions.assignBookingRoom(booking.id, outcome.roomId)
    await actions.reloadBookings()
    actions.setEditSuccess(`Đã chốt phòng ${updated.roomNumber} cho booking ${booking.bookingCode}.`)
    actions.setRoomBooking(null)
  } catch (err) {
    actions.setRoomError(err instanceof Error ? err.message : 'Không thể gán phòng.')
  } finally {
    actions.setRoomSaving(false)
  }
}
