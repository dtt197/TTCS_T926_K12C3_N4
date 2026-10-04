import { useState, type FormEvent } from 'react'
import { searchAvailableRooms, type RoomAvailabilityResponse } from '../services/roomService'
import './AuthPages.css'
import './GuestBookingPage.css'
import './PublicRoomSearchPage.css'

const MAX_STAY_NIGHTS = 30

function todayIso() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function addDays(isoDate: string, days: number) {
  const date = new Date(`${isoDate}T00:00:00`)
  date.setDate(date.getDate() + days)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function nightsBetween(checkIn: string, checkOut: string) {
  const start = new Date(`${checkIn}T00:00:00`)
  const end = new Date(`${checkOut}T00:00:00`)
  return Math.round((end.getTime() - start.getTime()) / 86_400_000)
}

function getErrorMessage(err: unknown) {
  if (err instanceof TypeError) return 'Không kết nối được tới homestay, vui lòng thử lại sau.'
  return err instanceof Error ? err.message : 'Không tra cứu được phòng, vui lòng thử lại.'
}

export function PublicRoomSearchPage() {
  const [checkIn, setCheckIn] = useState('')
  const [checkOut, setCheckOut] = useState('')
  const [guestCount, setGuestCount] = useState('1')
  const [results, setResults] = useState<RoomAvailabilityResponse[]>([])
  const [hasSearched, setHasSearched] = useState(false)
  const [isSearching, setIsSearching] = useState(false)
  const [error, setError] = useState('')

  async function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')

    if (!checkIn || !checkOut) {
      setError('Vui lòng chọn ngày nhận phòng và ngày trả phòng.')
      return
    }
    if (checkIn < todayIso()) {
      setError('Ngày nhận phòng không được trước ngày hiện tại.')
      return
    }
    if (checkOut <= checkIn) {
      setError('Ngày trả phòng phải sau ngày nhận phòng.')
      return
    }
    if (nightsBetween(checkIn, checkOut) > MAX_STAY_NIGHTS) {
      setError('Khoảng thời gian tra cứu tối đa là 30 đêm.')
      return
    }

    const guests = Number(guestCount)
    if (!Number.isInteger(guests) || guests < 1) {
      setError('Số khách phải là số nguyên lớn hơn hoặc bằng 1.')
      return
    }

    setIsSearching(true)
    try {
      const availableRooms = await searchAvailableRooms(checkIn, checkOut, guests)
      setResults(availableRooms)
      setHasSearched(true)
    } catch (err) {
      setError(getErrorMessage(err))
      setHasSearched(false)
    } finally {
      setIsSearching(false)
    }
  }

  const minCheckOut = checkIn ? addDays(checkIn, 1) : todayIso()
  const maxCheckOut = checkIn ? addDays(checkIn, MAX_STAY_NIGHTS) : undefined
  const selectedNights = checkIn && checkOut && checkOut > checkIn
    ? nightsBetween(checkIn, checkOut)
    : 0

  return (
    <main className="login-layout public-room-search">
      <section className="brand-panel" aria-label="HomeStay">
        <div>
          <p className="eyebrow">HomeStay</p>
          <h1>Tìm phòng.</h1>
          <p className="brand-copy">
            Chọn ngày lưu trú và số khách để xem loại phòng còn trống phù hợp.
          </p>
        </div>
        <div className="brand-footer">
          <span className="brand-mark" aria-hidden="true">H</span>
          <span>Tra cứu không cần đăng nhập</span>
        </div>
      </section>

      <section className="form-panel">
        <div className="login-form">
          <p className="eyebrow">Tra cứu phòng trống</p>
          <h2>Chọn kỳ lưu trú</h2>
          <p className="form-intro">Số khách được so với sức chứa tối đa của từng phòng.</p>

          {error && <p className="error-message" role="alert">{error}</p>}

          <form onSubmit={handleSearch} noValidate>
            <div className="field-row">
              <div className="field">
                <label htmlFor="search-check-in">Ngày nhận phòng</label>
                <input
                  id="search-check-in"
                  type="date"
                  min={todayIso()}
                  value={checkIn}
                  onChange={(event) => {
                    const nextCheckIn = event.target.value
                    setCheckIn(nextCheckIn)
                    if (nextCheckIn && (!checkOut || checkOut <= nextCheckIn)) {
                      setCheckOut(addDays(nextCheckIn, 1))
                    }
                    setHasSearched(false)
                    setError('')
                  }}
                  required
                />
              </div>
              <div className="field">
                <label htmlFor="search-check-out">
                  Ngày trả phòng {selectedNights > 0 && `(${selectedNights} đêm)`}
                </label>
                <input
                  id="search-check-out"
                  type="date"
                  min={minCheckOut}
                  max={maxCheckOut}
                  value={checkOut}
                  onChange={(event) => {
                    setCheckOut(event.target.value)
                    setHasSearched(false)
                    setError('')
                  }}
                  required
                />
                {selectedNights > MAX_STAY_NIGHTS && (
                  <p className="field-error">Khoảng thời gian tra cứu tối đa là 30 đêm.</p>
                )}
              </div>
            </div>

            <div className="field">
              <label htmlFor="search-guest-count">Số khách</label>
              <input
                id="search-guest-count"
                type="number"
                min={1}
                step={1}
                value={guestCount}
                onChange={(event) => {
                  setGuestCount(event.target.value)
                  setHasSearched(false)
                  setError('')
                }}
                required
              />
            </div>

            <button className="submit-button" type="submit" disabled={isSearching}>
              {isSearching ? 'Đang tra phòng...' : 'Tra phòng'}
            </button>
          </form>

          {hasSearched && (
            results.length > 0 ? (
              <ul className="room-search-results" aria-label="Loại phòng còn trống">
                {results.map((room) => (
                  <li className="room-search-result" key={room.roomTypeId}>
                    <div>
                      <h3>{room.name}</h3>
                      <p>Sức chứa tối đa {room.capacity} khách</p>
                    </div>
                    <strong>Còn {room.availableRooms} phòng</strong>
                    <button
                      className="room-search-book-button"
                      type="button"
                      onClick={() => {
                        const params = new URLSearchParams({
                          roomTypeId: String(room.roomTypeId),
                          checkInDate: checkIn,
                          checkOutDate: checkOut,
                          guestCount,
                        })
                        window.location.assign(`/dat-phong?${params.toString()}`)
                      }}
                    >
                      Đặt phòng
                    </button>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="room-search-empty" role="status">Không còn phòng phù hợp</p>
            )
          )}
        </div>
      </section>
    </main>
  )
}
