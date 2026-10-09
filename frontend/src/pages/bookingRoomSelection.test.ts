import { getRoomSelectionOutcome } from './bookingRoomSelection.ts'
import type { AssignableRoom } from '../types/booking'
import type { BookingListItem } from '../types/booking'
import { confirmRoomSelection, reloadAssignableRooms } from './bookingRoomActions.ts'

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

assertOutcome('current temporary room can be confirmed', getRoomSelectionOutcome('105', 105, rooms), { type: 'assign', roomId: 105 })
assertOutcome('different eligible room can be confirmed', getRoomSelectionOutcome('105', 106, rooms), { type: 'assign', roomId: 106 })
assertOutcome('new assignment', getRoomSelectionOutcome(null, 105, rooms), { type: 'assign', roomId: 105 })
assertOutcome('invalid selection', getRoomSelectionOutcome(null, 999, rooms), { type: 'invalid-selection' })

const booking: BookingListItem = {
  id: 301, bookingCode: 'BK-2026-0301', guestName: 'Nguyen Van A', roomTypeId: 1,
  roomTypeNameSnapshot: 'Standard', checkInDate: '2026-10-15', checkOutDate: '2026-10-17',
  totalAmount: 2000000, status: 'DA_XAC_NHAN', holdExpired: false, roomNumber: null,
}

async function runRoomActionTests() {
  let modalBooking: BookingListItem | null = booking
  let availableRooms: AssignableRoom[] = rooms
  let selectedRoomId: number | null = 105
  let roomError: string | null = null
  let roomLoading = false
  let roomSaving = false
  const errors: Array<string | null> = []
  let reloadCalls = 0
  let availableRoomsCalls = 0
  let responseRooms: AssignableRoom[] = rooms
  let assignmentError: Error | null = new Error('Phòng xung đột với booking BK-2026-0999')
  let assignmentRoomId = 105
  let assignmentCalls = 0
  let loadError: Error | null = null

  const actions = {
    getAvailableRooms: async (bookingId: number) => {
      availableRoomsCalls += 1
      assertOutcome('availability API receives active booking id', bookingId, booking.id)
      if (loadError) throw loadError
      return responseRooms
    },
    assignBookingRoom: async (_bookingId: number, roomId: number) => {
      assignmentCalls += 1
      assignmentRoomId = roomId
      if (assignmentError) throw assignmentError
      return { ...booking, roomNumber: roomId === 105 ? '105' : '106', roomConfirmedAt: '2026-10-09T12:00:00Z', roomConfirmedBy: 'Lễ tân' }
    },
    setAssignableRooms: (value: AssignableRoom[]) => { availableRooms = value },
    setSelectedRoomId: (value: number | null) => { selectedRoomId = value },
    setRoomError: (value: string | null) => { roomError = value; errors.push(value) },
    setRoomLoading: (value: boolean) => { roomLoading = value },
    setRoomSaving: (value: boolean) => { roomSaving = value },
    setRoomBooking: (value: BookingListItem | null) => { modalBooking = value },
    setEditSuccess: (_value: string | null) => undefined,
    reloadBookings: async () => { reloadCalls += 1 },
  }

  await confirmRoomSelection(booking, selectedRoomId, availableRooms, actions)
  assertOutcome('conflict displays conflicting booking code', errors.some((value) => value?.includes('BK-2026-0999')), true)
  assertOutcome('assignment failure keeps dialog booking', modalBooking?.bookingCode, booking.bookingCode)
  assertOutcome('assignment failure does not close dialog', modalBooking !== null, true)
  assertOutcome('assignment failure resets saving state', roomSaving, false)
  assertOutcome('assignment failure does not optimistically confirm room', booking.roomConfirmedAt ?? null, null)

  selectedRoomId = 105
  responseRooms = [rooms[0], { id: 301, roomNumber: '301', floor: 3, roomType: 'Standard', status: 'AVAILABLE' }]
  await reloadAssignableRooms(modalBooking, selectedRoomId, actions)
  assertOutcome('reload calls availability API', availableRoomsCalls, 1)
  assertOutcome('reload replaces available room options', availableRooms.map((room) => room.id), [105, 301])
  assertOutcome('reload keeps selected room when still available', selectedRoomId, 105)
  assertOutcome('successful reload retains booking', modalBooking?.bookingCode, booking.bookingCode)
  assertOutcome('successful reload ends loading', roomLoading, false)

  responseRooms = [{ id: 301, roomNumber: '301', floor: 3, roomType: 'Standard', status: 'AVAILABLE' }]
  await reloadAssignableRooms(modalBooking, selectedRoomId, actions)
  assertOutcome('unavailable selection is cleared', selectedRoomId, null)
  assertOutcome('unavailable selection shows message', String(roomError).includes('không còn khả dụng'), true)
  assertOutcome('unavailable selection keeps booking', modalBooking?.bookingCode, booking.bookingCode)

  selectedRoomId = 105
  responseRooms = []
  await reloadAssignableRooms(modalBooking, selectedRoomId, actions)
  assertOutcome('empty list shows no-room message', errors.some((value) => value?.includes('không còn phòng phù hợp')), true)
  assertOutcome('empty list clears stale room selection', selectedRoomId, null)

  selectedRoomId = 105
  availableRooms = rooms
  loadError = new Error('Không kết nối được API phòng')
  await reloadAssignableRooms(modalBooking, selectedRoomId, actions)
  assertOutcome('reload failure shows API error', roomError, loadError.message)
  assertOutcome('reload failure keeps booking details', modalBooking?.bookingCode, booking.bookingCode)
  assertOutcome('reload failure keeps dialog open', modalBooking !== null, true)
  assertOutcome('reload failure keeps selection', selectedRoomId, 105)
  assertOutcome('reload failure keeps previous room options', availableRooms.map((room) => room.id), [105, 106])

  loadError = null
  assignmentError = null
  responseRooms = rooms
  await reloadAssignableRooms(modalBooking, selectedRoomId, actions)
  selectedRoomId = 105
  await confirmRoomSelection(modalBooking, selectedRoomId, availableRooms, actions)
  assertOutcome('confirming current temporary room calls API', assignmentRoomId, 105)
  assertOutcome('successful assignment closes dialog', modalBooking, null)
  assertOutcome('successful assignment reloads list', reloadCalls, 1)

  modalBooking = booking
  assignmentError = null
  selectedRoomId = 106
  await confirmRoomSelection(modalBooking, selectedRoomId, availableRooms, actions)
  assertOutcome('confirming a different room calls API', assignmentRoomId, 106)
  assertOutcome('different-room success closes dialog', modalBooking, null)
  assertOutcome('successful confirmations call assignment API', assignmentCalls, 3)
  assertOutcome('each successful confirmation reloads backend bookings', reloadCalls, 2)
}

void runRoomActionTests()
