import { useEffect, useState } from 'react'
import { getRoomTypes } from '../services/roomTypeService'
import { createWalkInBooking, getGuestQuote } from '../services/guestBookingService'
import { searchAvailableRooms } from '../services/roomService'
import type { RoomType } from '../types/roomType'
import type { WalkInBookingResult } from '../types/guestBooking'
import { WalkInBookingReceipt } from '../components/WalkInBookingReceipt'
import './WalkInBookingPage.css'

type WalkInForm = {
  roomTypeId: string
  checkInDate: string
  checkOutDate: string
  guestCount: string
  guestName: string
  phone: string
  email: string
  note: string
  depositAmount: string
  depositPaymentMethod: 'CASH' | 'BANK_TRANSFER'
  depositPaymentReference: string
}

function todayIso() {
  const now = new Date()

  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function addDays(isoDate: string, days: number) {
  const date = new Date(`${isoDate}T00:00:00`)
  date.setDate(date.getDate() + days)

  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function emptyForm(): WalkInForm {
  return {
    roomTypeId: '',
    checkInDate: todayIso(),
    checkOutDate: addDays(todayIso(), 1),
    guestCount: '1',
    guestName: '',
    phone: '',
    email: '',
    note: '',
    depositAmount: '0',
    depositPaymentMethod: 'CASH',
    depositPaymentReference: '',
  }
}

export function WalkInBookingPage() {
  const [today] = useState(todayIso)

  const [form, setForm] = useState<WalkInForm>(emptyForm)

  const [roomTypes, setRoomTypes] = useState<RoomType[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  // S3-06 Lát 4: booking vừa tạo, hiện ra để lễ tân đọc hoặc in cho khách.
  const [created, setCreated] = useState<WalkInBookingResult | null>(null)
  // Số phòng còn trống theo loại phòng và khoảng ngày đã chọn.
  const [availabilityRefresh, setAvailabilityRefresh] = useState(0)
  const [availabilityResult, setAvailabilityResult] = useState<{
    key: string
    availableRooms: number | null
  } | null>(null)
  const [quoteResult, setQuoteResult] = useState<{
    key: string
    totalAmount: number | null
  } | null>(null)

  useEffect(() => {
    getRoomTypes()
      .then((data) => {
        setRoomTypes(data.filter((roomType) => roomType.active))
      })
      .catch((err: unknown) => {
        setError(
          err instanceof Error
            ? err.message
            : 'Không tải được danh sách loại phòng.',
        )
      })
      .finally(() => {
        setLoading(false)
      })
  }, [])

  const selectedRoomType = roomTypes.find(
    (roomType) => String(roomType.id) === form.roomTypeId,
  )

  const guestCount = Number(form.guestCount)
  const depositAmount = Number(form.depositAmount)
  const quoteQueryKey = Number.isInteger(Number(form.roomTypeId)) &&
    Number(form.roomTypeId) > 0 &&
    Boolean(form.checkInDate && form.checkOutDate && form.checkOutDate > form.checkInDate) &&
    Number.isInteger(guestCount) && guestCount >= 1
    ? `${form.roomTypeId}|${form.checkInDate}|${form.checkOutDate}|${guestCount}`
    : null
  const quotedTotal = quoteQueryKey !== null && quoteResult?.key === quoteQueryKey
    ? quoteResult.totalAmount
    : null
  const availabilityQueryKey = quoteQueryKey === null
    ? null
    : `${quoteQueryKey}|${availabilityRefresh}`
  const currentAvailabilityResult = availabilityQueryKey !== null &&
    availabilityResult?.key === availabilityQueryKey
    ? availabilityResult
    : null
  const availableRooms = currentAvailabilityResult?.availableRooms ?? null
  const checkingAvailability = availabilityQueryKey !== null && currentAvailabilityResult === null

  useEffect(() => {
    if (quoteQueryKey === null) return
    const roomTypeId = Number(form.roomTypeId)
    let cancelled = false
    getGuestQuote(roomTypeId, form.checkInDate, form.checkOutDate, guestCount)
      .then((quote) => {
        if (!cancelled) setQuoteResult({ key: quoteQueryKey, totalAmount: quote.totalAmount })
      })
      .catch(() => {
        if (!cancelled) setQuoteResult({ key: quoteQueryKey, totalAmount: null })
      })
    return () => { cancelled = true }
  }, [quoteQueryKey, form.roomTypeId, form.checkInDate, form.checkOutDate, guestCount])

  useEffect(() => {
    if (availabilityQueryKey === null) return
    const roomTypeId = Number(form.roomTypeId)
    const guests = Number(form.guestCount)
    let cancelled = false
    searchAvailableRooms(
      form.checkInDate,
      form.checkOutDate,
      guests,
    )
      .then((results) => {
        if (cancelled) return

        const selected = results.find(
          (room) => room.roomTypeId === roomTypeId,
        )

        setAvailabilityResult({
          key: availabilityQueryKey,
          availableRooms: selected?.availableRooms ?? 0,
        })
      })
      .catch(() => {
        if (!cancelled) {
          setAvailabilityResult({ key: availabilityQueryKey, availableRooms: null })
        }
      })

    return () => {
      cancelled = true
    }
  }, [
    form.roomTypeId,
    form.checkInDate,
    form.checkOutDate,
    form.guestCount,
    availabilityQueryKey,
  ])


  function update(
    field: keyof WalkInForm,
    value: string,
  ) {
    if (
      (field === 'roomTypeId' || field === 'checkInDate' || field === 'checkOutDate' || field === 'guestCount') &&
      form[field] !== value
    ) {
      setQuoteResult(null)
      setAvailabilityResult(null)
    }
    setForm((current) => ({
      ...current,
      [field]: value,
    }))

    setError('')
    setMessage('')
  }

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()

    setError('')
    setMessage('')

    if (!form.guestName.trim()) {
      setError('Vui lòng nhập họ tên khách.')
      return
    }

    if (!form.phone.trim()) {
      setError('Vui lòng nhập số điện thoại.')
      return
    }

    if (!/^(03|05|07|08|09)[0-9]{8}$/.test(form.phone.trim())) {
      setError('Số điện thoại phải có đúng 10 chữ số Việt Nam.')
      return
    }

    if (
      form.email.trim() &&
      !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())
    ) {
      setError('Email không đúng định dạng.')
      return
    }

    if (!form.roomTypeId) {
      setError('Vui lòng chọn loại phòng.')
      return
    }

    if (!form.checkInDate) {
      setError('Vui lòng chọn ngày nhận phòng.')
      return
    }

    if (!form.checkOutDate) {
      setError('Vui lòng chọn ngày trả phòng.')
      return
    }

    if (form.checkOutDate <= form.checkInDate) {
      setError('Ngày trả phòng phải sau ngày nhận phòng.')
      return
    }

    if (
      !Number.isInteger(guestCount) ||
      guestCount < 1
    ) {
      setError('Số khách phải lớn hơn hoặc bằng 1.')
      return
    }

    if (
      selectedRoomType &&
      guestCount > selectedRoomType.maxCapacity
    ) {
      setError(
        `${selectedRoomType.name} chỉ nhận tối đa ${selectedRoomType.maxCapacity} khách.`,
      )
      return
    }

    if (availableRooms === null) {
      setError('Chưa kiểm tra được số phòng trống. Vui lòng thử lại.')
      return
    }

    if (availableRooms < 1) {
      setError('Loại phòng đã hết phòng trong khoảng ngày bạn chọn.')
      return
    }

    if (!Number.isSafeInteger(depositAmount) || depositAmount < 0) {
      setError('Tiền cọc phải là số nguyên không âm.')
      return
    }
    if (quotedTotal === null) {
      setError('Chưa tính được tổng tiền booking. Vui lòng thử lại.')
      return
    }
    if (depositAmount > quotedTotal) {
      setError('Tiền cọc không được vượt tổng tiền booking đã chốt.')
      return
    }
    if (depositAmount > 0 && form.depositPaymentMethod === 'BANK_TRANSFER' && !form.depositPaymentReference.trim()) {
      setError('Chuyển khoản phải có mã giao dịch.')
      return
    }

    try {
      const result = await createWalkInBooking({
        roomTypeId: Number(form.roomTypeId),
        checkInDate: form.checkInDate,
        checkOutDate: form.checkOutDate,
        guestName: form.guestName.trim(),
        phone: form.phone.trim(),
        email: form.email.trim() || undefined,
        guestCount,
        note: form.note.trim() || undefined,
        depositAmount,
        depositPaymentMethod: depositAmount > 0 ? form.depositPaymentMethod : undefined,
        depositPaymentReference: depositAmount > 0 ? form.depositPaymentReference.trim() || undefined : undefined,
      })

      setCreated(result)
      setAvailabilityRefresh((current) => current + 1)
    } catch (err: unknown) {
      setError(
        err instanceof Error
          ? err.message
          : 'Không thể tạo booking tại quầy.',
      )
    }
  }

  function handleCreateAnother() {
    setCreated(null)
    setForm(emptyForm())
    setError('')
    setMessage('')
  }

  if (loading) {
    return (
      <section className="walk-in-page">
        <div className="walk-in-card">
          <p>Đang tải danh sách loại phòng...</p>
        </div>
      </section>
    )
  }

  return (
    <section className="walk-in-page">
      <div className="walk-in-header">
        <div>

          <h2>Booking tại quầy</h2>

          <p>
            Nhập thông tin lưu trú cho khách vãng lai.
            Ngày nhận phòng mặc định là hôm nay.
          </p>
        </div>
      </div>

      {created ? (
        <WalkInBookingReceipt
          booking={created}
          onCreateAnother={handleCreateAnother}
        />
      ) : (
        <form
          className="walk-in-card"
          onSubmit={handleSubmit}
          noValidate
        >
        {error && (
          <div
            className="walk-in-message error"
            role="alert"
          >
            {error}
          </div>
        )}

        {message && (
          <div
            className="walk-in-message success"
            role="status"
          >
            {message}
          </div>
        )}

        <div className="walk-in-grid">
          <label>
            <span>Họ tên khách</span>

            <input
              type="text"
              value={form.guestName}
              maxLength={120}
              placeholder="Nhập họ tên khách"
              onChange={(event) =>
                update('guestName', event.target.value)
              }
            />
          </label>

          <label>
            <span>Số điện thoại</span>

            <input
              type="tel"
              value={form.phone}
              maxLength={10}
              placeholder="Nhập số điện thoại"
              onChange={(event) =>
                update('phone', event.target.value)
              }
            />
          </label>

              <label>
                <span>Email</span>

                <input
                  type="email"
                  value={form.email}
                  maxLength={150}
                  placeholder="Không bắt buộc"
                  onChange={(event) =>
                    update('email', event.target.value)
                  }
                />

                <small>
                  Booking tại quầy không gửi email cho khách.
                </small>
              </label>
          <label>
            <span>Loại phòng</span>

            <select
              value={form.roomTypeId}
              onChange={(event) =>
                update(
                  'roomTypeId',
                  event.target.value,
                )
              }
            >
              <option value="">
                -- Chọn loại phòng --
              </option>

              {roomTypes.map((roomType) => (
                <option
                  key={roomType.id}
                  value={roomType.id}
                >
                  {roomType.name} · tối đa{' '}
                  {roomType.maxCapacity} khách
                </option>
              ))}
            </select>
          </label>

          <label>
            <span>Ngày nhận phòng</span>

            <input
              type="date"
              value={form.checkInDate}
              min={today}
              onChange={(event) =>
                update(
                  'checkInDate',
                  event.target.value,
                )
              }
            />

            <small>
              Mặc định: hôm nay
            </small>
          </label>

          <label>
            <span>Ngày trả phòng</span>

            <input
              type="date"
              value={form.checkOutDate}
              min={addDays(form.checkInDate || today, 1)}
              onChange={(event) =>
                update(
                  'checkOutDate',
                  event.target.value,
                )
              }
            />
          </label>

          <label>
            <span>Số khách</span>

            <input
              type="number"
              min="1"
              max={selectedRoomType?.maxCapacity ?? 99}
              value={form.guestCount}
              onChange={(event) =>
                update(
                  'guestCount',
                  event.target.value,
                )
              }
            />

            {selectedRoomType && (
              <small>
                Tối đa {selectedRoomType.maxCapacity}{' '}
                khách
              </small>
            )}
          </label>

          {form.roomTypeId && (
            <div
              className={`walk-in-message ${checkingAvailability
                  ? ''
                  : availableRooms === null
                    ? 'error'
                    : availableRooms > 0
                      ? 'success'
                      : 'error'
                }`}
              role="status"
            >
              {checkingAvailability
                ? 'Đang kiểm tra số phòng trống...'
                : availableRooms === null
                  ? 'Chưa lấy được số phòng trống. Vui lòng kiểm tra lại.'
                  : availableRooms > 0
                    ? `Còn ${availableRooms} phòng trống trong khoảng ngày đã chọn.`
                    : 'Loại phòng này đã hết phòng trong khoảng ngày đã chọn.'}
            </div>
          )}

          <label>
            <span>Ghi chú</span>

            <textarea
              value={form.note}
              maxLength={500}
              placeholder="Ghi chú cho booking (không bắt buộc)"
              onChange={(event) =>
                update('note', event.target.value)
              }
            />
          </label>

          <label>
            <span>Tiền cọc đã thu (đ)</span>
            <input type="number" min="0" step="1" value={form.depositAmount}
              onChange={(event) => update('depositAmount', event.target.value)} />
            <small>{quotedTotal === null ? 'Tổng tiền booking chưa tính được.' : `Tổng tiền đã chốt: ${new Intl.NumberFormat('vi-VN').format(quotedTotal)} đ`}</small>
          </label>

          {depositAmount > 0 && <>
            <label>
              <span>Phương thức nhận cọc</span>
              <select value={form.depositPaymentMethod} onChange={(event) => update('depositPaymentMethod', event.target.value)}>
                <option value="CASH">Tiền mặt</option>
                <option value="BANK_TRANSFER">Chuyển khoản</option>
              </select>
            </label>
            {form.depositPaymentMethod === 'BANK_TRANSFER' && <label>
              <span>Mã giao dịch</span>
              <input type="text" maxLength={255} required value={form.depositPaymentReference}
                onChange={(event) => update('depositPaymentReference', event.target.value)} />
            </label>}
          </>}

        </div>

        <div className="walk-in-actions">
          <button
            type="submit"
            className="walk-in-primary"
          >
            Tạo booking
          </button>
        </div>
        </form>
      )}
    </section>
  )
}
