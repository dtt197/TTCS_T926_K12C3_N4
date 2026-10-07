import { useEffect, useRef, useState, type FormEvent } from 'react'
import { getPublicRoomTypes } from '../services/guestBookingService'
import { searchAvailableRooms, type RoomAvailabilityResponse } from '../services/roomService'
import type { PublicRoomTypeOption } from '../types/guestBooking'
import './AuthPages.css'
import './GuestBookingPage.css'
import './PublicRoomSearchPage.css'

const MAX_STAY_NIGHTS = 30

/** S2-04 AC4: mở từ trang chi tiết loại phòng (/tim-phong?roomTypeId=...) thì loại phòng đó được chọn sẵn. */
function roomTypeIdFromUrl() {
  const id = Number(new URLSearchParams(window.location.search).get('roomTypeId'))
  return Number.isSafeInteger(id) && id > 0 ? String(id) : ''
}

function todayIso() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
}

function addMonths(isoDate: string, months: number) {
  const [year, month, day] = isoDate.split('-').map(Number)
  const target = new Date(year, month - 1 + months, 1)
  const lastDay = new Date(target.getFullYear(), target.getMonth() + 1, 0).getDate()
  target.setDate(Math.min(day, lastDay))
  return `${target.getFullYear()}-${String(target.getMonth() + 1).padStart(2, '0')}-${String(target.getDate()).padStart(2, '0')}`
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
  const initialSearchParams = new URLSearchParams(window.location.search)
  const [today] = useState(todayIso)
  const maxCheckInDate = addMonths(today, 12)
  // S2-04 AC4: loại phòng chọn sẵn; '' = tất cả loại phòng (đúng S2-05 khi vào thẳng trang)
  const [roomTypeId, setRoomTypeId] = useState(roomTypeIdFromUrl)
  const [roomTypes, setRoomTypes] = useState<PublicRoomTypeOption[] | null>(null)
  const [checkIn, setCheckIn] = useState(() => initialSearchParams.get('checkInDate') ?? '')
  const [checkOut, setCheckOut] = useState(() => initialSearchParams.get('checkOutDate') ?? '')
  const [guestCount, setGuestCount] = useState(() => initialSearchParams.get('guestCount') ?? '1')
  const [results, setResults] = useState<RoomAvailabilityResponse[]>([])
  const [hasSearched, setHasSearched] = useState(false)
  const [isSearching, setIsSearching] = useState(false)
  const [error, setError] = useState('')
  const autoSearchQuery = useRef<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getPublicRoomTypes()
      .then((list) => {
        if (!cancelled) setRoomTypes(list)
      })
      .catch(() => {
        // Không tải được danh sách thì vẫn tra được tất cả loại phòng như trước
        if (!cancelled) setRoomTypes([])
      })
    return () => {
      cancelled = true
    }
  }, [])

  async function searchRooms(checkInValue: string, checkOutValue: string, guestCountValue: string) {
    setError('')

    if (!checkInValue || !checkOutValue) {
      setError('Vui lòng chọn ngày nhận phòng và ngày trả phòng.')
      return
    }
    if (checkInValue < today) {
      setError('Ngày nhận phòng không được trước ngày hiện tại.')
      return
    }
    if (checkInValue > maxCheckInDate) {
      setError('Ngày nhận phòng không được quá 12 tháng kể từ ngày hiện tại.')
      return
    }
    if (checkOutValue <= checkInValue) {
      setError('Ngày trả phòng phải sau ngày nhận phòng.')
      return
    }
    if (nightsBetween(checkInValue, checkOutValue) > MAX_STAY_NIGHTS) {
      setError('Khoảng thời gian tra cứu tối đa là 30 đêm.')
      return
    }

    const guests = Number(guestCountValue)
    if (!Number.isInteger(guests) || guests < 1) {
      setError('Số khách phải là số nguyên lớn hơn hoặc bằng 1.')
      return
    }

    setIsSearching(true)
    try {
      const availableRooms = await searchAvailableRooms(checkInValue, checkOutValue, guests)
      setResults(availableRooms)
      setHasSearched(true)
    } catch (err) {
      setError(getErrorMessage(err))
      setHasSearched(false)
    } finally {
      setIsSearching(false)
    }
  }

  async function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    await searchRooms(checkIn, checkOut, guestCount)
  }

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const checkInParam = params.get('checkInDate')
    const checkOutParam = params.get('checkOutDate')
    const guestCountParam = params.get('guestCount')

    if (checkInParam === null || checkOutParam === null || guestCountParam === null) {
      return
    }

    const queryKey = `${checkInParam}|${checkOutParam}|${guestCountParam}`
    if (autoSearchQuery.current === queryKey) {
      return
    }
    autoSearchQuery.current = queryKey
    void searchRooms(checkInParam, checkOutParam, guestCountParam)
  }, [])

  const minCheckOut = checkIn ? addDays(checkIn, 1) : today
  const maxCheckOut = checkIn ? addDays(checkIn, MAX_STAY_NIGHTS) : undefined
  const selectedNights = checkIn && checkOut && checkOut > checkIn
    ? nightsBetween(checkIn, checkOut)
    : 0

  // Loại phòng trên đường dẫn không còn bán (không có trong danh sách) thì coi như tra tất cả
  const selectedRoomType = roomTypes?.find((roomType) => String(roomType.id) === roomTypeId) ?? null
  const activeRoomTypeId = roomTypes === null || selectedRoomType ? roomTypeId : ''
  const visibleResults = activeRoomTypeId
    ? results.filter((room) => String(room.roomTypeId) === activeRoomTypeId)
    : results

  return (
    <main className="login-layout public-room-search">
      <section className="brand-panel" aria-label="HomeStay">
        <div className="room-search-hero-copy">
          <p className="eyebrow">HomeStay</p>
          <h1>Tìm phòng<br />phù hợp.</h1>
          <p className="brand-copy">
            Chọn ngày lưu trú và số khách để khám phá những căn phòng phù hợp cho kỳ nghỉ của bạn.
          </p>
          <ul className="room-search-benefits" aria-label="Lợi ích khi tra cứu">
            <li>
              <span className="room-search-benefit-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24"><path d="m5 12.5 4.2 4.2L19 7" /></svg>
              </span>
              <span>Phòng trống theo thời gian thực</span>
            </li>
            <li>
              <span className="room-search-benefit-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24"><path d="m5 12.5 4.2 4.2L19 7" /></svg>
              </span>
              <span>Giá rõ ràng</span>
            </li>
            <li>
              <span className="room-search-benefit-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24"><path d="m5 12.5 4.2 4.2L19 7" /></svg>
              </span>
              <span>Đặt phòng nhanh chóng</span>
            </li>
          </ul>
        </div>
        <div className="brand-footer">
          <span className="brand-mark" aria-hidden="true">H</span>
          <span className="room-search-hero-note">Không cần tài khoản · Tra cứu nhanh chóng</span>
        </div>
      </section>

      <section className="form-panel">
        <div className="login-form">
          <header className="room-search-form-header">
            <p className="eyebrow">Tra cứu phòng trống</p>
            <h2>Chọn kỳ lưu trú</h2>
            <p className="form-intro">Chọn loại phòng, thời gian lưu trú và số khách.</p>
          </header>
          {selectedRoomType && (
            <p className="room-search-selected-hint">
              Đã chọn sẵn loại phòng <strong>{selectedRoomType.name}</strong> từ trang chi tiết.
            </p>
          )}

          {error && <p className="error-message" role="alert">{error}</p>}

          <form onSubmit={handleSearch} noValidate>
            <div className="field">
              <label htmlFor="search-room-type">Loại phòng</label>
              <select
                id="search-room-type"
                value={activeRoomTypeId}
                disabled={roomTypes === null}
                onChange={(event) => setRoomTypeId(event.target.value)}
              >
                <option value="">Tất cả loại phòng</option>
                {(roomTypes ?? []).map((roomType) => (
                  <option key={roomType.id} value={String(roomType.id)}>
                    {roomType.name}
                  </option>
                ))}
              </select>
            </div>

            <div className="field-row">
              <div className="field">
                <label htmlFor="search-check-in">Ngày nhận phòng</label>
                <input
                  id="search-check-in"
                  type="date"
                  min={today}
                  max={maxCheckInDate}
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
                  max={maxCheckOut ?? addDays(maxCheckInDate, MAX_STAY_NIGHTS)}
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
            <ul className="room-search-trust" aria-label="Cam kết dịch vụ">
              <li>
                <span aria-hidden="true">✓</span>
                <span>Phòng trống</span>
              </li>
              <li>
                <span aria-hidden="true">✓</span>
                <span>Giá tốt nhất</span>
              </li>
              <li>
                <span aria-hidden="true">✓</span>
                <span>An tâm lưu trú</span>
              </li>
            </ul>
          </form>

          {hasSearched && (
            visibleResults.length > 0 ? (
              <ul className="room-search-results" aria-label="Loại phòng còn trống">
                {visibleResults.map((room) => (
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
            ) : activeRoomTypeId && results.length > 0 ? (
              <div className="room-search-selected-unavailable" role="status">
                <p>
                  Loại phòng {selectedRoomType?.name} không còn phòng trống phù hợp trong khoảng ngày này.
                </p>
                <button
                  className="room-search-show-all"
                  type="button"
                  onClick={() => setRoomTypeId('')}
                >
                  Xem các loại phòng khác còn trống
                </button>
              </div>
            ) : (
              <p className="room-search-empty" role="status">Không còn phòng phù hợp</p>
            )
          )}
        </div>
      </section>
    </main>
  )
}