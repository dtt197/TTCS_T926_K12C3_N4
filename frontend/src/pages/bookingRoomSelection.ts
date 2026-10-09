import type { AssignableRoom } from '../types/booking'

export type RoomSelectionOutcome =
  | { type: 'assign'; roomId: number }
  | { type: 'invalid-selection' }

export function getRoomSelectionOutcome(
  _currentRoomNumber: string | null | undefined,
  selectedRoomId: number,
  rooms: AssignableRoom[],
): RoomSelectionOutcome {
  const selectedRoom = rooms.find((room) => room.id === selectedRoomId)
  if (!selectedRoom) return { type: 'invalid-selection' }
  return { type: 'assign', roomId: selectedRoomId }
}
