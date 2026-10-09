import type { BookingListItem, BookingRoomChangeHistory } from '../types/booking'
import { submitRoomChange } from './bookingRoomChangeActions.ts'

const booking: BookingListItem = {
  id: 9, bookingCode: 'BK-9', guestName: 'Guest', roomTypeId: 1,
  roomTypeNameSnapshot: 'Standard', checkInDate: '2026-10-09', checkOutDate: '2026-10-11',
  totalAmount: 200000, status: 'DA_XAC_NHAN', holdExpired: false, roomNumber: '101',
}
const history: BookingRoomChangeHistory = {
  id: 1, bookingId: 9, oldRoomId: 10, oldRoomNumber: '101', newRoomId: 11,
  newRoomNumber: '102', actorUserId: 2, actorName: 'Receptionist',
  changedAt: '2026-10-09T09:00:00Z', reason: 'Repair',
}

function equal(name: string, actual: unknown, expected: unknown) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`${name}: ${JSON.stringify(actual)}`)
  console.log(`PASS ${name}`)
}

async function main() {
  let error: string | null = null
  let activeBooking: BookingListItem | null = booking
  let saving = false
  let historyRows: BookingRoomChangeHistory[] = []
  let apiCalls = 0
  let historyCalls = 0
  let apiError: Error | null = null
  const actions = {
    changeBookingRoom: async (_id: number, _roomId: number, _reason: string) => {
      apiCalls += 1
      if (apiError) throw apiError
      return booking
    },
    reloadBookingData: async () => undefined,
    getBookingRoomChangeHistory: async (_id: number) => { historyCalls += 1; return [history] },
    setRoomChangeHistory: (rows: BookingRoomChangeHistory[]) => { historyRows = rows },
    setRoomError: (value: string | null) => { error = value },
    setRoomSaving: (value: boolean) => { saving = value },
    setChangeRoomBooking: (value: BookingListItem | null) => { activeBooking = value },
    setEditSuccess: (_value: string | null) => undefined,
  }

  await submitRoomChange(booking, 11, '  ', actions)
  equal('blank reason is required without API request', [error, apiCalls], ['Vui lòng nhập lý do đổi phòng.', 0])
  apiError = new Error('API conflict')
  await submitRoomChange(booking, 11, 'Repair', actions)
  equal('API error keeps modal and clears saving state', [error, activeBooking?.id, saving, historyCalls], ['API conflict', 9, false, 0])
  apiError = null
  await submitRoomChange(booking, 11, 'Repair', actions)
  equal('success reloads history from server and closes modal', [historyCalls, historyRows, activeBooking, saving], [1, [history], null, false])
}

void main()
