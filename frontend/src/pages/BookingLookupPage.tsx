import { useState, type FormEvent } from 'react'
import { ApiRequestError } from '../services/apiClient'
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

/** Giờ dạng HH:mm:ss từ máy chủ, hiển thị HH:mm. */
function formatTime(isoTime: string) {
  return isoTime.slice(0, 5)
}

/** Lỗi mạng (fetch ném TypeError, ví dụ "Failed to fetch") đổi thành câu tiếng Việt cho khách. */
function errorMessage(err: unknown) {
  if (err instanceof TypeError) return 'Không kết nối được tới homestay, vui lòng thử lại sau'
  return err instanceof Error ? err.message : 'Không tra cứu được booking, vui lòng thử lại'
}

/** S2-08 Lát 3: mã booking 8 ký tự gồm chữ và số (S2-07). */
function codeError(code: string) {
  if (!code.trim()) return 'Vui lòng nhập mã booking'
  if (!/^[A-Za-z0-9]{8}$/.test(code.trim())) return 'Mã booking gồm đúng 8 ký tự chữ và số'
  return ''
}

/** S2-08 Lát 3: cùng quy tắc email với trang đặt phòng. */
function emailError(email: string) {
  if (!email.trim()) return 'Vui lòng nhập email'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) return 'Email không đúng định dạng'
  return ''
}

/** S2-08 Lát 3: mở từ đường dẫn /tra-cuu-booking?ma=XXXXXXXX thì điền sẵn mã. */
function codeFromUrl() {
  return new URLSearchParams(window.location.search).get('ma')?.trim() ?? ''
}

/** S2-08: khách nhập mã booking và email để xem lại booking, không cần tài khoản. */
export function BookingLookupPage() {
  const [bookingCode, setBookingCode] = useState(codeFromUrl)
  const [email, setEmail] = useState('')
  const [fieldErrors, setFieldErrors] = useState({ bookingCode: '', email: '' })
  const [result, setResult] = useState<BookingLookupResult | null>(null)
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)
  // S2-08 Lát 2: quá 10 lần tra cứu sai trong 15 phút thì thông báo hiện màu cảnh báo riêng
  const [limited, setLimited] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const errors = { bookingCode: codeError(bookingCode), email: emailError(email) }
    setFieldErrors(errors)
    if (errors.bookingCode || errors.email) {
      setError('')
      return
    }

    setSubmitting(true)
    setError('')
    try {
      setResult(await lookupBooking(bookingCode.trim(), email.trim()))
    } catch (err) {
      setResult(null)
      setLimited(err instanceof ApiRequestError && err.status === 429)
      setError(errorMessage(err))
    } finally {
      setSubmitting(false)
    }
  }

  function handleLookupAnother() {
    setResult(null)
    setBookingCode('')
    setEmail('')
    setFieldErrors({ bookingCode: '', email: '' })
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
              <dt>Nhận phòng</dt>
              <dd>{formatDate(result.checkInDate)}, từ {formatTime(result.checkInTime)}</dd>
              <dt>Trả phòng</dt>
              <dd>{formatDate(result.checkOutDate)}, trước {formatTime(result.checkOutTime)}</dd>
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

            {error && (
              <p className={limited ? 'error-message booking-lookup-limit' : 'error-message'} role="alert">
                {error}
              </p>
            )}

            <div className="field">
              <label htmlFor="lookup-code">Mã booking</label>
              <input
                id="lookup-code"
                className="booking-lookup-code-input"
                value={bookingCode}
                onChange={(event) => {
                  setBookingCode(event.target.value)
                  setFieldErrors((current) => ({ ...current, bookingCode: '' }))
                }}
                placeholder="VD: ABCD2345"
                autoComplete="off"
                autoCapitalize="characters"
                aria-invalid={Boolean(fieldErrors.bookingCode)}
                aria-describedby={fieldErrors.bookingCode ? 'lookup-code-error' : undefined}
              />
              {fieldErrors.bookingCode && (
                <p id="lookup-code-error" className="field-error">{fieldErrors.bookingCode}</p>
              )}
            </div>

            <div className="field">
              <label htmlFor="lookup-email">Email đã dùng khi đặt phòng</label>
              <input
                id="lookup-email"
                type="email"
                value={email}
                onChange={(event) => {
                  setEmail(event.target.value)
                  setFieldErrors((current) => ({ ...current, email: '' }))
                }}
                placeholder="email@example.com"
                autoComplete="email"
                autoFocus={Boolean(bookingCode)}
                aria-invalid={Boolean(fieldErrors.email)}
                aria-describedby={fieldErrors.email ? 'lookup-email-error' : undefined}
              />
              {fieldErrors.email && (
                <p id="lookup-email-error" className="field-error">{fieldErrors.email}</p>
              )}
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