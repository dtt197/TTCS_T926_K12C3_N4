import { useEffect, useState, type FormEvent } from 'react'
import { GuestQuoteTable } from '../components/GuestQuoteTable'
import { createGuestBooking, getPublicRoomTypes } from '../services/guestBookingService'
import type { GuestBookingResult, PublicRoomTypeOption } from '../types/guestBooking'
import './AuthPages.css'
import './GuestBookingPage.css'

type FormState = {
  roomTypeId: string
  checkInDate: string
  checkOutDate: string
  guestName: string
  phone: string
  email: string
  guestCount: string
  note: string
  acceptedCancellationPolicy: boolean
}

const EMPTY_FORM: FormState = {
  roomTypeId: '',
  checkInDate: '',
  checkOutDate: '',
  guestName: '',
  phone: '',
  email: '',
  guestCount: '1',
  note: '',
  acceptedCancellationPolicy: false,
}

const money = new Intl.NumberFormat('vi-VN')

/** Ngày hôm nay theo giờ máy, dạng yyyy-MM-dd (dùng làm ngày nhỏ nhất cho ô chọn ngày). */
function todayIso() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

/** Lỗi mạng (fetch ném TypeError, ví dụ "Failed to fetch") đổi thành câu tiếng Việt cho khách. */
function errorMessage(err: unknown, fallback: string) {
  if (err instanceof TypeError) return 'Không kết nối được tới homestay, vui lòng thử lại sau'
  return err instanceof Error ? err.message : fallback
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

function validateVietnamesePhone(phone: string) {
  return /^(03|05|07|08|09)\d{8}$/.test(phone.trim())
}

function validateEmail(email: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())
}

/** S2-07 Lát 1: khách điền thông tin và gửi yêu cầu đặt phòng, không cần đăng nhập. */
export function GuestBookingPage() {
  const [today] = useState(todayIso)
  const [roomTypes, setRoomTypes] = useState<PublicRoomTypeOption[]>([])
  const [form, setForm] = useState<FormState>(EMPTY_FORM)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [result, setResult] = useState<GuestBookingResult | null>(null)

  useEffect(() => {
    getPublicRoomTypes()
      .then(setRoomTypes)
      .catch((err: unknown) => {
        setError(errorMessage(err, 'Không tải được danh sách loại phòng'))
      })
  }, [])

  const selectedRoomType = roomTypes.find((roomType) => String(roomType.id) === form.roomTypeId)
  const guestCount = Number(form.guestCount)
  const overCapacity = Boolean(selectedRoomType && Number.isInteger(guestCount)
    && guestCount > selectedRoomType.maxCapacity)

  function update(field: keyof FormState, value: string) {
    setForm((current) => ({ ...current, [field]: value }))
  }

// Tính số đêm giữa ngày nhận và ngày trả
const calculateNights = (checkIn: string, checkOut: string) => {
  if (!checkIn || !checkOut) return 0;
  const start = new Date(checkIn);
  const end = new Date(checkOut);
  const diffTime = end.getTime() - start.getTime();
  const diffDays = Math.round(diffTime / (1000 * 60 * 60 * 24));
  return diffDays;
};

const nights = calculateNights(form.checkInDate, form.checkOutDate);
  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setFieldErrors({})

    const errors: Record<string, string> = {}

    if (!form.roomTypeId) {
      errors.roomTypeId = 'Vui lòng chọn loại phòng'
    }

    if (!form.checkInDate) {
      errors.checkInDate = 'Vui lòng chọn ngày nhận phòng'
    }

    if (!form.checkOutDate) {
      errors.checkOutDate = 'Vui lòng chọn ngày trả phòng'
    }

    if (!form.guestName.trim()) {
      errors.guestName = 'Vui lòng nhập họ tên'
    }

    if (!form.phone.trim()) {
      errors.phone = 'Vui lòng nhập số điện thoại'
    } else if (!validateVietnamesePhone(form.phone)) {
      errors.phone = 'Số điện thoại phải có đúng 10 chữ số Việt Nam'
    }

    if (!form.email.trim()) {
      errors.email = 'Vui lòng nhập email'
    } else if (!validateEmail(form.email)) {
      errors.email = 'Email không đúng định dạng'
    }

    if (!form.acceptedCancellationPolicy) {
      errors.acceptedCancellationPolicy =
        'Bạn phải xác nhận đã đọc chính sách hủy'
    }

    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors)
      setError('Vui lòng kiểm tra lại các thông tin chưa hợp lệ')
      return
    }

    // Ngày dạng yyyy-MM-dd nên so sánh chuỗi là đúng thứ tự thời gian. Máy chủ vẫn kiểm tra lại.
    if (form.checkOutDate <= form.checkInDate) {
      setError('Ngày trả phòng phải sau ngày nhận phòng ít nhất một đêm')
      return
    }
    if (nights > 30) {
      setError('Khoảng thời gian tra cứu tối đa là 30 đêm')
      return
    }
    if (!Number.isInteger(guestCount) || guestCount < 1) {
      setError(`Số khách phải từ 1 đến ${selectedRoomType?.maxCapacity ?? 'sức chứa tối đa'}`)
      return
    }
    if (overCapacity && selectedRoomType) {
      setError(`${selectedRoomType.name} chỉ nhận tối đa ${selectedRoomType.maxCapacity} khách, bạn đang chọn ${guestCount} khách`)
      return
    }

   setIsSubmitting(true)
    try {
      // Kiểm tra số lượng phòng trống thực tế theo thời gian khách chọn
      const availabilities = await searchAvailableRooms(
        form.checkInDate, 
        form.checkOutDate, 
        Number(form.guestCount)
      );
      
      const selectedRoomAvailability = availabilities.find(
        (item) => String(item.roomTypeId) === form.roomTypeId
      );

      if (!selectedRoomAvailability || selectedRoomAvailability.availableRooms <= 0) {
        setError('Rất tiếc, loại phòng này đã hết phòng trống trong khoảng thời gian bạn chọn.');
        setIsSubmitting(false);
        return;
      }

      // Nếu còn phòng thì tiến hành gửi yêu cầu đặt phòng
      const created = await createGuestBooking({
        roomTypeId: Number(form.roomTypeId),
        checkInDate: form.checkInDate,
        checkOutDate: form.checkOutDate,
        guestName: form.guestName,
        phone: form.phone,
        email: form.email,
        guestCount,
        note: form.note,
        acceptedCancellationPolicy: form.acceptedCancellationPolicy,
      })
      setResult(created)
    } catch (err) {
      setError(errorMessage(err, 'Không gửi được yêu cầu đặt phòng'))
    } finally {
      setIsSubmitting(false)
    }
  }

  function handleBookAnother() {
    setResult(null)
    setForm(EMPTY_FORM)
  }

  return (
    <main className="login-layout guest-booking">
      <section className="brand-panel" aria-label="HomeStay">
        <div>
          <p className="eyebrow">HomeStay</p>
          <h1>Đặt phòng.</h1>
          <p className="brand-copy">
            Gửi yêu cầu ngay khi còn phòng. Homestay giữ chỗ cho bạn 24 giờ trong lúc lễ tân xác nhận.
          </p>
        </div>
        <div className="brand-footer">
          <span className="brand-mark" aria-hidden="true">H</span>
          <span>Không cần tạo tài khoản</span>
        </div>
      </section>

      <section className="form-panel">
        {result ? (
          <div className="login-form guest-booking-success" role="status">
            <p className="eyebrow">Đã gửi yêu cầu</p>
            <h2>Cảm ơn bạn!</h2>
            <p className="form-intro">Hãy lưu lại mã booking để tra cứu sau.</p>
            <div className="guest-booking-code">
              <span>Mã booking</span>
              <strong>{result.bookingCode}</strong>
            </div>
            <dl className="guest-booking-summary">
              <dt>Trạng thái</dt>
              <dd><span className="guest-booking-status">{result.statusLabel}</span></dd>
              <dt>Loại phòng</dt>
              <dd>{result.roomTypeName}</dd>
              <dt>Ngày ở</dt>
              <dd>{formatDate(result.checkInDate)} – {formatDate(result.checkOutDate)} ({result.nights} đêm)</dd>
              <dt>Tạm tính</dt>
              <dd>{money.format(result.totalAmount)} đ</dd>
              <dt>Giữ chỗ đến</dt>
              <dd>{formatDateTime(result.holdExpiresAt)}</dd>
            </dl>
            <button className="submit-button" type="button" onClick={handleBookAnother}>
              Đặt thêm phòng khác
            </button>
          </div>
        ) : (
          <form className="login-form" onSubmit={handleSubmit} noValidate>
            <p className="eyebrow">Yêu cầu đặt phòng</p>
            <h2>Thông tin đặt phòng</h2>
            <p className="form-intro">Điền thông tin bên dưới, lễ tân sẽ liên hệ xác nhận.</p>

            {error && <p className="error-message" role="alert">{error}</p>}

            <div className="field">
              <label htmlFor="guest-room-type">Loại phòng</label>
              <select id="guest-room-type" value={form.roomTypeId}
                onChange={(event) => update('roomTypeId', event.target.value)}>
                <option value="">Chọn loại phòng...</option>
                {roomTypes.map((roomType) => (
                  <option key={roomType.id} value={roomType.id}>
                    {roomType.name} (tối đa {roomType.maxCapacity} khách)
                  </option>
                ))}
              </select>
            </div>

           <div className="field-row">
  <div className="field">
    <label htmlFor="guest-check-in">Ngày nhận phòng</label>
    <input 
      id="guest-check-in" 
      type="date" 
      min={today} 
      value={form.checkInDate}
      onChange={(event) => {
        const newCheckIn = event.target.value;
        let newCheckOut = form.checkOutDate;
        if (!newCheckOut || newCheckOut <= newCheckIn) {
          const d = new Date(newCheckIn);
          d.setDate(d.getDate() + 1);
          newCheckOut = d.toISOString().split('T')[0];
        }
        setForm(current => ({
          ...current,
          checkInDate: newCheckIn,
          checkOutDate: newCheckOut
        }));
      }} 
    />
  </div>
  
  <div className="field">
    <label htmlFor="guest-check-out">
      Ngày trả phòng {nights > 0 && `(${nights} đêm)`}
    </label>
    <input 
      id="guest-check-out" 
      type="date" 
      min={form.checkInDate ? (() => {
        const d = new Date(form.checkInDate);
        d.setDate(d.getDate() + 1);
        return d.toISOString().split('T')[0];
      })() : today} 
      value={form.checkOutDate}
      onChange={(event) => update('checkOutDate', event.target.value)} 
    />
    {nights > 30 && (
      <p className="field-error">Khoảng thời gian tra cứu tối đa là 30 đêm.</p>
    )}
  </div>
</div>
              
            <GuestQuoteTable
              roomTypeId={form.roomTypeId}
              roomTypeName={selectedRoomType?.name ?? ''}
              maxCapacity={selectedRoomType?.maxCapacity ?? Number.MAX_SAFE_INTEGER}
              checkInDate={form.checkInDate}
              checkOutDate={form.checkOutDate}
              guestCount={form.guestCount}
              onSelectRoomType={(roomTypeId) => update('roomTypeId', String(roomTypeId))}
            />
            <div className="field">
              <label htmlFor="guest-name">Họ tên</label>
              <input id="guest-name" autoComplete="name" maxLength={120} value={form.guestName}
                onChange={(event) => update('guestName', event.target.value)} />
            </div>

            <div className="field-row">
              <div className="field">
                <label htmlFor="guest-phone">Số điện thoại</label>
                <input
                  id="guest-phone"
                  type="tel"
                  autoComplete="tel"
                  maxLength={15}
                  value={form.phone}
                  onChange={(event) => {
                    update('phone', event.target.value)
                    setFieldErrors((current) => ({ ...current, phone: '' }))
                  }}
                  aria-invalid={Boolean(fieldErrors.phone)}
                />

                {fieldErrors.phone && (
                  <p className="field-error">{fieldErrors.phone}</p>
                )}
              </div>
              <div className="field">
                <label htmlFor="guest-count">Số khách</label>
                <input id="guest-count" type="number" min={1} max={selectedRoomType?.maxCapacity}
                  value={form.guestCount} onChange={(event) => update('guestCount', event.target.value)} />
                  {selectedRoomType && (
                  <p className="guest-capacity-hint">
                    Tiêu chuẩn {selectedRoomType.standardCapacity}, tối đa {selectedRoomType.maxCapacity} khách
                  </p>
                )}
              </div>
            </div>

            <div className="field">
              <label htmlFor="guest-email">Email</label>
              <input
                id="guest-email"
                type="email"
                autoComplete="email"
                maxLength={150}
                value={form.email}
                onChange={(event) => {
                  update('email', event.target.value)
                  setFieldErrors((current) => ({ ...current, email: '' }))
                }}
                aria-invalid={Boolean(fieldErrors.email)}
              />

              {fieldErrors.email && (
                <p className="field-error">{fieldErrors.email}</p>
              )}
            </div>

            <div className="field">
              <label htmlFor="guest-note">Ghi chú (không bắt buộc)</label>
              <textarea id="guest-note" rows={3} maxLength={500} value={form.note}
                placeholder="Ví dụ: đến muộn khoảng 22h"
                onChange={(event) => update('note', event.target.value)} />
            </div>

            <div className="policy-checkbox">
              <label>
                <input
                  type="checkbox"
                  checked={form.acceptedCancellationPolicy}
                  onChange={(event) => {
                    setForm((current) => ({
                      ...current,
                      acceptedCancellationPolicy: event.target.checked,
                    }))

                    setFieldErrors((current) => ({
                      ...current,
                      acceptedCancellationPolicy: '',
                    }))
                  }}
                />

                <span>
                  Tôi xác nhận đã đọc và đồng ý với chính sách hủy phòng.
                </span>
              </label>

              {fieldErrors.acceptedCancellationPolicy && (
                <p className="field-error">
                  {fieldErrors.acceptedCancellationPolicy}
                </p>
              )}
            </div>

            <button className="submit-button" type="submit" disabled={isSubmitting || overCapacity}>
              {isSubmitting ? 'Đang gửi...' : 'Gửi yêu cầu đặt phòng'}
            </button>
          </form>
        )}
      </section>
    </main>
  )
}