import { useState, type FormEvent } from 'react'
import { lookupBooking } from '../services/bookingLookupService'
import type { BookingLookupResult } from '../types/bookingLookup'
import './AuthPages.css'
import './GuestBookingPage.css'
import './BookingLookupPage.css'

const money = new Intl.NumberFormat('vi-VN')

const STATUS_CLASS_NAMES: Record<string, string> = {
  CHO_XAC_NHAN: 'pending',
  DA_XAC_NHAN: 'confirmed',
  DA_HUY: 'cancelled',
  DA_NHAN_PHONG: 'checked-in',
  DA_TRA_PHONG: 'checked-out',
  DA_HET_HAN: 'cancelled',
}

function formatDate(isoDate: string) {
  const [year, month, day] = isoDate.split('-')
  return `${day}/${month}/${year}`
}

/** Lỗi mạng (fetch ném TypeError, ví dụ "Failed to fetch") đổi thành câu tiếng Việt cho khách. */
function errorMessage(err: unknown) {
  if (err instanceof TypeError) return 'Không kết nối được tới homestay, vui lòng thử lại sau'
  return err instanceof Error ? err.message : 'Không tra cứu được booking, vui lòng thử lại'
}

/** S2-08: khách nhập mã booking và email để xem lại booking, không cần tài khoản. */
export function BookingLookupPage() {
  const [bookingCode, setBookingCode] = useState('')
  const [email, setEmail] = useState('')
  const [result, setResult] = useState<BookingLookupResult | null>(null)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!bookingCode.trim() || !email.trim()) {
      setError('Vui lòng nhập mã booking và email')
      return
    }

    setSubmitting(true)
    setError('')
    try {
      setResult(await lookupBooking(bookingCode.trim(), email.trim()))
    } catch (err) {
      setResult(null)
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  function handleLookupAnother() {
    setResult(null)
    setBookingCode('')
    setEmail('')
    setError('')
  }

  return (
    <main className="login-layout guest-booking booking-lookup">
      <section className="brand-panel" aria-label="HomeStay">
        <div>
          <p className="eyebrow">HomeStay</p>
          <h1>Tra cứu booking.</h1>
          <p className="brand-copy">
            Nhập mã booking và email bạn đã dùng khi đặt phòng để xem lại ngày ở, trạng thái và số tiền.
          </p>
        </div>
        <div className="brand-footer">
          <span className="brand-mark" aria-hidden="true">H</span>
          <span>Không cần tạo tài khoản</span>
        </div>
      </section>

      <section className="form-panel">
        {result ? (
          <div className="login-form booking-lookup-result" role="status">
            <p className="eyebrow">Chi tiết booking</p>
            <h2>{result.roomTypeName}</h2>
            <div className="guest-booking-code">
              <span>Mã booking</span>
              <strong>{result.bookingCode}</strong>
            </div>
            <dl className="guest-booking-summary">
              <dt>Trạng thái</dt>
              <dd>
                <span className={`booking-lookup-status ${STATUS_CLASS_NAMES[result.status] ?? ''}`}>
                  {result.statusLabel}
                </span>
              </dd>
              <dt>Loại phòng</dt>
              <dd>{result.roomTypeName}</dd>
              <dt>Ngày nhận phòng</dt>
              <dd>{formatDate(result.checkInDate)}</dd>
              <dt>Ngày trả phòng</dt>
              <dd>{formatDate(result.checkOutDate)}</dd>
              <dt>Số đêm</dt>
              <dd>{result.nights} đêm</dd>
              <dt>Tổng tiền</dt>
              <dd>{money.format(result.totalAmount)} đ</dd>
              <dt>Tiền cọc đã ghi nhận</dt>
              <dd>
                {result.depositAmount > 0
                  ? `${money.format(result.depositAmount)} đ`
                  : <span className="booking-lookup-muted">Chưa ghi nhận</span>}
              </dd>
            </dl>
            <button className="submit-button" type="button" onClick={handleLookupAnother}>
              Tra cứu booking khác
            </button>
          </div>
        ) : (
          <form className="login-form" onSubmit={handleSubmit} noValidate>
            <p className="eyebrow">Tra cứu booking</p>
            <h2>Xem lại đặt phòng</h2>
            <p className="form-intro">Mã booking gồm 8 ký tự, có trong thông báo khi bạn đặt phòng thành công.</p>

            {error && <p className="error-message" role="alert">{error}</p>}

            <div className="field">
              <label htmlFor="lookup-code">Mã booking</label>
              <input
                id="lookup-code"
                className="booking-lookup-code-input"
                value={bookingCode}
                onChange={(event) => setBookingCode(event.target.value)}
                placeholder="VD: ABCD2345"
                autoComplete="off"
                autoCapitalize="characters"
              />
            </div>

            <div className="field">
              <label htmlFor="lookup-email">Email đã dùng khi đặt phòng</label>
              <input
                id="lookup-email"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                placeholder="email@example.com"
                autoComplete="email"
              />
            </div>

            <button className="submit-button" type="submit" disabled={submitting}>
              {submitting ? 'Đang tra cứu...' : 'Tra cứu'}
            </button>
          </form>
        )}
      </section>
    </main>
  )
}