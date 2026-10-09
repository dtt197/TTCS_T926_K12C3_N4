import { useEffect, useState, type FormEvent } from 'react'
import { GuestQuoteTable } from '../components/GuestQuoteTable'
import { createGuestBooking, getPublicRoomTypes } from '../services/guestBookingService'
import { searchAvailableRooms, type RoomAvailabilityResponse } from '../services/roomService'
import { ApiRequestError } from '../services/apiClient'
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

function initialFormFromSearchParams(): FormState {
  const params = new URLSearchParams(window.location.search)
  return {
    ...EMPTY_FORM,
    roomTypeId: params.get('roomTypeId') ?? '',
    checkInDate: params.get('checkInDate') ?? '',
    checkOutDate: params.get('checkOutDate') ?? '',
    guestCount: params.get('guestCount') ?? EMPTY_FORM.guestCount,
  }
}

const money = new Intl.NumberFormat('vi-VN')

/** Ngày hôm nay theo giờ máy, dạng yyyy-MM-dd (dùng làm ngày nhỏ nhất cho ô chọn ngày). */
function todayIso() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function addDays(isoDate: string, days: number) {
  const date = new Date(`${isoDate}T00:00:00`)
  date.setDate(date.getDate() + days)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function addMonths(isoDate: string, months: number) {
  const [year, month, day] = isoDate.split('-').map(Number)
  const target = new Date(year, month - 1 + months, 1)
  const lastDay = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate()
  target.setDate(Math.min(day, lastDay))
  return `${target.getFullYear()}-${String(target.getMonth() + 1).padStart(2, '0')}-${String(target.getDate()).padStart(2, '0')}`
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
  const maxCheckInDate = addMonths(today, 12)
  const [roomTypes, setRoomTypes] = useState<PublicRoomTypeOption[]>([])
  const [form, setForm] = useState<FormState>(initialFormFromSearchParams)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [result, setResult] = useState<GuestBookingResult | null>(null)
    // S3-02 Lát 2: loại phòng khác còn trống để gợi ý khi loại phòng đang chọn vừa hết.
  const [alternatives, setAlternatives] = useState<RoomAvailabilityResponse[]>([])

  function suggestAlternatives(availabilities: RoomAvailabilityResponse[]) {
    setAlternatives(availabilities.filter(
      (item) => item.availableRooms > 0 && String(item.roomTypeId) !== form.roomTypeId,
    ))
  }

  function chooseAlternative(roomTypeId: number) {
    setForm((current) => ({ ...current, roomTypeId: String(roomTypeId) }))
    setAlternatives([])
    setError('')
  }

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
    setAlternatives([])

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

    if (form.checkInDate && form.checkInDate > maxCheckInDate) {
      errors.checkInDate = 'Ngày nhận phòng không được quá 12 tháng kể từ ngày hiện tại'
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
        suggestAlternatives(availabilities);
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
      // S3-02 Lát 2: khách khác vừa đặt mất phòng cuối cùng (409) → gợi ý loại phòng khác, thông tin đã nhập vẫn giữ nguyên.
      if (err instanceof ApiRequestError && err.status === 409) {
        searchAvailableRooms(form.checkInDate, form.checkOutDate, Number(form.guestCount))
          .then(suggestAlternatives)
          .catch(() => setAlternatives([]))
      }
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
        <div className="guest-booking-hero-copy">
          <p className="eyebrow">Homestay</p>
          <h1>Đặt phòng<br />cho kỳ nghỉ<br />của bạn.</h1>
          <p className="brand-copy">
            Gửi yêu cầu đặt phòng nhanh chóng. Homestay giữ chỗ trong 24 giờ để lễ tân xác nhận.
          </p>
          <ul className="guest-booking-benefits" aria-label="Lợi ích khi đặt phòng">
            <li><span aria-hidden="true">✓</span> Xác nhận nhanh</li>
            <li><span aria-hidden="true">✓</span> Giá minh bạch</li>
            <li><span aria-hidden="true">✓</span> Không cần tài khoản</li>
          </ul>
        </div>
        <div className="brand-footer">
          <span className="brand-mark" aria-hidden="true">H</span>
          <span className="guest-booking-hero-note">Tra cứu booking dễ dàng sau khi gửi yêu cầu.</span>
        </div>
      </section>

      <section className="form-panel">
        {result ? (
          <div className="login-form guest-booking-success" role="status">
            <header className="guest-booking-form-header">
              <p className="eyebrow">Đã gửi yêu cầu</p>
              <h2>Cảm ơn bạn!</h2>
              <p className="form-intro">Hãy lưu lại mã booking để tra cứu sau.</p>
            </header>
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
            {/* S2-08 Lát 3: mở trang tra cứu với mã booking điền sẵn */}
            <a
              className="guest-booking-lookup-link"
              href={`/tra-cuu-booking?ma=${encodeURIComponent(result.bookingCode)}`}
            >
              Tra cứu booking này
            </a>
            <button className="submit-button" type="button" onClick={handleBookAnother}>
              Đặt thêm phòng khác
            </button>
          </div>
        ) : (
          <form className="login-form" onSubmit={handleSubmit} noValidate>
            <div className="guest-booking-back">
              <a href="/danh-sach-loai-phong">
                ← Quay lại xem danh sách phòng
              </a>
            </div>
            <header className="guest-booking-form-header">
              <p className="eyebrow">Yêu cầu đặt phòng</p>
              <h2>Thông tin đặt phòng</h2>
              <p className="form-intro">Điền thông tin bên dưới, lễ tân sẽ liên hệ xác nhận.</p>
            </header>

            {error && <p className="error-message" role="alert">{error}</p>}
            {alternatives.length > 0 && (
              <div className="guest-booking-alternatives">
                <p>Loại phòng khác còn trống trong khoảng ngày bạn chọn:</p>
                <div className="guest-booking-alternatives__list">
                  {alternatives.map((item) => (
                    <button key={item.roomTypeId} type="button" onClick={() => chooseAlternative(item.roomTypeId)}>
                      {item.name} · còn {item.availableRooms} phòng
                    </button>
                  ))}
                </div>
              </div>
            )}
            <section className="guest-booking-section" aria-labelledby="guest-stay-heading">
              <h3 className="guest-booking-section-title" id="guest-stay-heading">Thông tin lưu trú</h3>
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
                {selectedRoomType && (
                  <a className="guest-room-type-details-link" href={`/loai-phong/${selectedRoomType.id}`}>
                    Xem chi tiết loại phòng
                  </a>
                )}
              </div>

              <div className="field-row guest-booking-stay-row">
                <div className="field">
                  <label htmlFor="guest-check-in">Ngày nhận phòng</label>
                  <input
                    id="guest-check-in"
                    type="date"
                    min={today}
                    max={maxCheckInDate}
                    value={form.checkInDate}
                    onChange={(event) => {
                      const newCheckIn = event.target.value;
                      let newCheckOut = form.checkOutDate;
                      if (!newCheckOut || newCheckOut <= newCheckIn) {
                        newCheckOut = addDays(newCheckIn, 1)
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
                      return addDays(form.checkInDate, 1)
                    })() : today}
                    max={form.checkInDate
                      ? addDays(form.checkInDate, 30)
                      : addDays(maxCheckInDate, 30)}
                    value={form.checkOutDate}
                    onChange={(event) => update('checkOutDate', event.target.value)}
                  />
                  {nights > 30 && (
                    <p className="field-error">Khoảng thời gian tra cứu tối đa là 30 đêm.</p>
                  )}
                </div>

                <div className="field guest-booking-guest-count">
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
            </section>

            <div className="guest-booking-section guest-booking-pricing" aria-label="Giá tạm tính">
              <GuestQuoteTable
                roomTypeId={form.roomTypeId}
                roomTypeName={selectedRoomType?.name ?? ''}
                maxCapacity={selectedRoomType?.maxCapacity ?? Number.MAX_SAFE_INTEGER}
                checkInDate={form.checkInDate}
                checkOutDate={form.checkOutDate}
                guestCount={form.guestCount}
                onSelectRoomType={(roomTypeId) => update('roomTypeId', String(roomTypeId))}
              />
            </div>

            <section className="guest-booking-section guest-booking-guest-details" aria-labelledby="guest-details-heading">
              <h3 className="guest-booking-section-title" id="guest-details-heading">Thông tin khách</h3>
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
              </div>

              <div className="field">
                <label htmlFor="guest-note">Ghi chú (không bắt buộc)</label>
                <textarea id="guest-note" rows={3} maxLength={500} value={form.note}
                  placeholder="Ví dụ: đến muộn khoảng 22h"
                  onChange={(event) => update('note', event.target.value)} />
              </div>
            </section>

            <section className="guest-booking-section guest-booking-confirmation" aria-labelledby="guest-confirmation-heading">
              <h3 className="guest-booking-section-title" id="guest-confirmation-heading">Xác nhận</h3>
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
            </section>
          </form>
        )}
      </section>
    </main>
  )
}