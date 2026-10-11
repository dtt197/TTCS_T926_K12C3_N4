import { bookingActionsFor } from './bookingActionVisibility.ts'
import type { BookingListItem } from '../types/booking'

const booking: BookingListItem = {
  id: 42, bookingCode: 'BK-42', guestName: 'Guest', roomTypeId: 1,
  roomTypeNameSnapshot: 'Standard', checkInDate: '2026-10-15', checkOutDate: '2026-10-17',
  totalAmount: 2000000, status: 'CHO_XAC_NHAN', holdExpired: false, roomNumber: null,
}

function equal(name: string, actual: unknown, expected: unknown) {
  if (JSON.stringify(actual) !== JSON.stringify(expected)) throw new Error(`${name}: ${JSON.stringify(actual)}`)
  console.log(`PASS ${name}`)
}

equal('pending booking shows permitted confirm, edit and cancel in order',
  bookingActionsFor(booking, { manage: true, cancel: true }, false), ['confirm', 'edit', 'cancel'])
equal('expired pending booking hides confirm and cancel permission stays status based',
  bookingActionsFor({ ...booking, holdExpired: true }, { manage: true, cancel: true }, false), ['edit', 'cancel'])
equal('confirmed unassigned booking shows assign room',
  bookingActionsFor({ ...booking, status: 'DA_XAC_NHAN' }, { manage: true, cancel: false }, false), ['assign-room', 'edit'])
equal('room change is supplied by existing role and date rule',
  bookingActionsFor({ ...booking, status: 'DA_NHAN_PHONG' }, { manage: false, cancel: false }, true), ['change-room'])
equal('unauthorized or ineligible booking has no actions',
  bookingActionsFor({ ...booking, status: 'DA_HUY' }, { manage: false, cancel: true }, false), [])
