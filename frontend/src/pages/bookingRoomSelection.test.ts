import { getRoomSelectionOutcome } from './bookingRoomSelection.ts'
import type { AssignableRoom } from '../types/booking'

const rooms: AssignableRoom[] = [
  { id: 105, roomNumber: '105', floor: 1, roomType: 'Standard', status: 'AVAILABLE' },
  { id: 106, roomNumber: '106', floor: 1, roomType: 'Standard', status: 'AVAILABLE' },
]

function assertOutcome(name: string, actual: unknown, expected: unknown) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) {
    throw new Error(`${name}: expected ${JSON.stringify(expected)}, received ${JSON.stringify(actual)}`)
  }
  console.log(`PASS ${name}`)
}

assertOutcome('unchanged room', getRoomSelectionOutcome('105', 105, rooms), { type: 'unchanged', roomNumber: '105' })
assertOutcome('unsupported change', getRoomSelectionOutcome('105', 106, rooms), { type: 'unsupported-change' })
assertOutcome('new assignment', getRoomSelectionOutcome(null, 105, rooms), { type: 'assign', roomId: 105 })
assertOutcome('invalid selection', getRoomSelectionOutcome(null, 999, rooms), { type: 'invalid-selection' })
