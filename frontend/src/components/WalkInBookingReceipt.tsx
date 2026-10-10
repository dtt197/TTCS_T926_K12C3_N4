import type { WalkInBookingResult } from '../types/guestBooking'
import './WalkInBookingReceipt.css'

type Props = {
  booking: WalkInBookingResult
  onCreateAnother: () => void
}

function formatDate(isoDate: string) {
  const [year, month, day] = isoDate.split('-')
  return `${day}/${month}/${year}`
}

function formatDateTime(isoDateTime: string) {
  return new Date(isoDateTime).toLocaleString('vi-VN', {
    timeZone: 'Asia/Ho_Chi_Minh',
    hour: '2-digit',
    minute: '2-digit',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  })
}

/** S3-06 Lát 4: lễ tân đọc thông tin xác nhận trên màn hình hoặc bấm In để đưa cho khách. */
export function WalkInBookingReceipt({ booking, onCreateAnother }: Props) {
  const room = booking.roomNumber
    ? `${booking.roomTypeName} · Phòng ${booking.roomNumber}`
    : booking.roomTypeName

  return (
    <div className="walk-in-confirmation">
      <div className="walk-in-receipt" role="status">
        <header className="walk-in-receipt-header">
          <p className="walk-in-receipt-eyebrow">HomeStay</p>
          <h3>Xác nhận booking</h3>
          <span className="walk-in-receipt-status">{booking.statusLabel}</span>
        </header>

        <div className="walk-in-receipt-code">
          <span>Mã booking</span>
          <strong>{booking.bookingCode}</strong>
        </div>

        <dl className="walk-in-receipt-list">
          <dt>Tên khách</dt>
          <dd>{booking.guestName}</dd>
          <dt>Phòng</dt>
          <dd>{room}</dd>
          <dt>Ngày nhận phòng</dt>
          <dd>{formatDate(booking.checkInDate)}</dd>
          <dt>Ngày trả phòng</dt>
          <dd>{formatDate(booking.checkOutDate)} ({booking.nights} đêm)</dd>
          <dt>Số khách</dt>
          <dd>{booking.guestCount}</dd>
          <dt>Trạng thái</dt>
          <dd>{booking.statusLabel}</dd>
          <dt>Nguồn booking</dt>
          <dd>{booking.sourceLabel}</dd>
          <dt>Tạo lúc</dt>
          <dd>{formatDateTime(booking.createdAt)}</dd>
        </dl>
      </div>

      <div className="walk-in-confirmation-actions">
        <button type="button" className="walk-in-secondary" onClick={onCreateAnother}>
          Tạo booking mới
        </button>
        <button type="button" className="walk-in-primary" onClick={() => window.print()}>
          In thông tin
        </button>
      </div>
    </div>
  )
}