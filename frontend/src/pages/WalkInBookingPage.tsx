import { useEffect, useState } from 'react'
import { getRoomTypes } from '../services/roomTypeService'
import type { RoomType } from '../types/roomType'
import './WalkInBookingPage.css'

type WalkInForm = {
  roomTypeId: string
  checkInDate: string
  checkOutDate: string
  guestCount: string
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

export function WalkInBookingPage() {
  const [today] = useState(todayIso)

  const [form, setForm] = useState<WalkInForm>(() => ({
    roomTypeId: '',
    checkInDate: todayIso(),
    checkOutDate: addDays(todayIso(), 1),
    guestCount: '1',
  }))

  const [roomTypes, setRoomTypes] = useState<RoomType[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')

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

  function update(
    field: keyof WalkInForm,
    value: string,
  ) {
    setForm((current) => ({
      ...current,
      [field]: value,
    }))

    setError('')
    setMessage('')
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()

    setError('')
    setMessage('')

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

    setMessage(
      'Thông tin booking tại quầy hợp lệ.',
    )
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
        </div>

        <div className="walk-in-actions">
          <button
            type="submit"
            className="walk-in-primary"
          >
            Kiểm tra thông tin
          </button>
        </div>
      </form>
    </section>
  )
}