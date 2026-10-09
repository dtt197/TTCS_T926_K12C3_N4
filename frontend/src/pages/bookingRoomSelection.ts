import type { AssignableRoom } from '../types/booking'

export type RoomSelectionOutcome =
  | { type: 'unchanged'; roomNumber: string }
  | { type: 'unsupported-change' }
  | { type: 'assign'; roomId: number }
  | { type: 'invalid-selection' }

export function getRoomSelectionOutcome(
  currentRoomNumber: string | null | undefined,
  selectedRoomId: number,
  rooms: AssignableRoom[],
): RoomSelectionOutcome {
  const selectedRoom = rooms.find((room) => room.id === selectedRoomId)
  if (!selectedRoom) return { type: 'invalid-selection' }
  if (currentRoomNumber) {
    return selectedRoom.roomNumber === currentRoomNumber
      ? { type: 'unchanged', roomNumber: selectedRoom.roomNumber }
      : { type: 'unsupported-change' }
  }
  return { type: 'assign', roomId: selectedRoomId }
}
