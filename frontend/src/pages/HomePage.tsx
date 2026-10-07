import { useEffect, useState, type FormEvent } from 'react'
import { PublicHeader } from '../components/PublicHeader'
import { getPublicRoomTypes } from '../services/guestBookingService'
import { getPublicRoomTypeCards } from '../services/publicRoomTypeService'
import { searchAvailableRooms, type RoomAvailabilityResponse } from '../services/roomService'
import type { PublicRoomTypeOption } from '../types/guestBooking'
import type { PublicRoomTypeCard } from '../types/publicRoomType'
import './RoomTypeListPage.css'
import './HomePage.css'

const HERO_IMAGE = '/assets/hero-room.jpg'
const DEFAULT_ROOM_IMAGE = '/room-images/room-1.jpg'
const money = new Intl.NumberFormat('vi-VN')

type SearchCriteria = {
  roomTypeId: string
  checkInDate: string
  checkOutDate: string
  guestCount: string
}

function todayIso(): string {
  const today = new Date()
  return `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
}

function addDays(isoDate: string, days: number): string {
  const date = new Date(`${isoDate}T00:00:00`)
  date.setDate(date.getDate() + days)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export function HomePage() {
  const [today] = useState(todayIso)
  const [roomTypes, setRoomTypes] = useState<PublicRoomTypeOption[]>([])
  const [roomTypeCards, setRoomTypeCards] = useState<PublicRoomTypeCard[]>([])
  const [roomTypeCardsLoadError, setRoomTypeCardsLoadError] = useState('')
  const [isLoadingRoomTypes, setIsLoadingRoomTypes] = useState(true)
  const [roomTypeLoadError, setRoomTypeLoadError] = useState('')
  const [roomTypeId, setRoomTypeId] = useState('')
  const [checkInDate, setCheckInDate] = useState('')
  const [checkOutDate, setCheckOutDate] = useState('')
  const [guestCount, setGuestCount] = useState('1')
  const [validationError, setValidationError] = useState('')
  const [searchError, setSearchError] = useState('')
  const [results, setResults] = useState<RoomAvailabilityResponse[]>([])
  const [hasSearched, setHasSearched] = useState(false)
  const [isSearching, setIsSearching] = useState(false)
  const [activeSearch, setActiveSearch] = useState<SearchCriteria | null>(null)

  useEffect(() => {
    let cancelled = false
    getPublicRoomTypes()
      .then((data) => {
        if (!cancelled) {
          setRoomTypes(data)
          setRoomTypeLoadError('')
        }
      })
      .catch(() => {
        if (!cancelled) {
          setRoomTypeLoadError('Không tải được danh sách loại phòng. Bạn vẫn có thể tra tất cả loại phòng.')
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoadingRoomTypes(false)
        }
      })

    getPublicRoomTypeCards()
      .then((data) => {
        if (!cancelled) {
          setRoomTypeCards(data)
          setRoomTypeCardsLoadError('')
        }
      })
      .catch(() => {
        if (!cancelled) {
          setRoomTypeCards([])
          setRoomTypeCardsLoadError('Không tải được ảnh minh họa loại phòng.')
        }
      })

    return () => {
      cancelled = true
    }
  }, [])

  async function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setValidationError('')
    setSearchError('')

    if (!checkInDate || !checkOutDate) {
      setValidationError('Vui lòng chọn ngày nhận phòng và ngày trả phòng.')
      return
    }
    if (checkInDate < today) {
      setValidationError('Ngày nhận phòng không được trước ngày hiện tại.')
      return
    }
    if (checkOutDate <= checkInDate) {
      setValidationError('Ngày trả phòng phải sau ngày nhận phòng.')
      return
    }

    const guests = Number(guestCount)
    if (!Number.isInteger(guests) || guests < 1) {
      setValidationError('Số khách phải là số nguyên lớn hơn hoặc bằng 1.')
      return
    }

    const criteria = {
      roomTypeId,
      checkInDate,
      checkOutDate,
      guestCount: String(guests),
    }
    setActiveSearch(criteria)
    setHasSearched(true)
    setIsSearching(true)
    setResults([])
    try {
      const availableRooms = await searchAvailableRooms(
        criteria.checkInDate,
        criteria.checkOutDate,
        guests,
      )
      setResults(availableRooms)
    } catch {
      setSearchError('Không thể tra phòng lúc này. Vui lòng thử lại.')
    } finally {
      setIsSearching(false)
    }
  }

  const visibleResults = activeSearch?.roomTypeId
    ? results.filter((room) => String(room.roomTypeId) === activeSearch.roomTypeId)
    : results
  const roomTypeCardById = new Map(roomTypeCards.map((card) => [card.id, card]))

  function bookingHref(roomTypeId: number) {
    if (!activeSearch) return '/dat-phong'
    const params = new URLSearchParams({
      roomTypeId: String(roomTypeId),
      checkInDate: activeSearch.checkInDate,
      checkOutDate: activeSearch.checkOutDate,
      guestCount: activeSearch.guestCount,
    })
    return `/dat-phong?${params.toString()}`
  }

  return (
    <div className="customer-home home-page">
      <PublicHeader />

      <main>
        <section className="customer-hero" aria-labelledby="home-hero-title">
          <div className="customer-hero__copy">
            <p className="customer-eyebrow">HOMESTAY</p>
            <h1 className="customer-hero__title" id="home-hero-title">
              Tìm kỳ nghỉ<br />phù hợp với bạn
            </h1>
            <p className="customer-hero__description">
              Không gian ấm cúng, tiện nghi hiện đại, gần gũi thiên nhiên.
              Trải nghiệm homestay thoải mái và đáng nhớ.
            </p>
            <div className="customer-hero__actions">
              <a className="customer-button customer-button--primary" href="/danh-sach-loai-phong">
                Xem các loại phòng
              </a>
              <a className="customer-button customer-button--secondary" href="/tra-cuu-dat-phong">
                Tra cứu booking
              </a>
            </div>
            <ul className="customer-benefits" aria-label="Điểm nổi bật">
              <li className="customer-benefit">
                <span className="customer-benefit__icon" aria-hidden="true">✓</span>
                <span className="customer-benefit__text">
                  <strong>Không gian xanh</strong>
                  <small>Gần gũi thiên nhiên</small>
                </span>
              </li>
              <li className="customer-benefit">
                <span className="customer-benefit__icon" aria-hidden="true">⌂</span>
                <span className="customer-benefit__text">
                  <strong>Tiện nghi đầy đủ</strong>
                  <small>Thoải mái như ở nhà</small>
                </span>
              </li>
              <li className="customer-benefit">
                <span className="customer-benefit__icon" aria-hidden="true">♡</span>
                <span className="customer-benefit__text">
                  <strong>Phù hợp mọi nhu cầu</strong>
                  <small>Cặp đôi, gia đình, nhóm bạn</small>
                </span>
              </li>
            </ul>
          </div>
          <div className="customer-hero__visual">
            <img src={HERO_IMAGE} alt="Phòng nghỉ ấm cúng nhìn ra núi xanh" />
            <div className="customer-hero__info">
              <span className="customer-hero__info-icon" aria-hidden="true">⌖</span>
              <span className="customer-hero__info-text">
                <strong>Không gian yên bình</strong>
                <small>Trải nghiệm trọn vẹn tại HomeStay</small>
              </span>
            </div>
          </div>
        </section>

        <section className="home-search" aria-label="Tìm phòng">
          <form className="home-search__card" onSubmit={handleSearch} noValidate>
            <p className="home-search__eyebrow">TÌM PHÒNG</p>
            <div className="home-search__fields">
              <div className="home-search__field home-search__field--room-type">
                <label htmlFor="home-search-room-type">Loại phòng</label>
                <select
                  id="home-search-room-type"
                  value={roomTypeId}
                  disabled={isLoadingRoomTypes}
                  onChange={(event) => setRoomTypeId(event.target.value)}
                >
                  <option value="">Tất cả loại phòng</option>
                  {roomTypes.map((roomType) => (
                    <option key={roomType.id} value={String(roomType.id)}>{roomType.name}</option>
                  ))}
                </select>
              </div>
              <div className="home-search__field home-search__field--check-in">
                <label htmlFor="home-search-check-in">Ngày nhận</label>
                <input
                  id="home-search-check-in"
                  type="date"
                  min={today}
                  value={checkInDate}
                  onChange={(event) => {
                    const nextDate = event.target.value
                    setCheckInDate(nextDate)
                    if (nextDate && (!checkOutDate || checkOutDate <= nextDate)) {
                      setCheckOutDate(addDays(nextDate, 1))
                    }
                  }}
                />
              </div>
              <div className="home-search__field home-search__field--check-out">
                <label htmlFor="home-search-check-out">Ngày trả</label>
                <input
                  id="home-search-check-out"
                  type="date"
                  min={checkInDate ? addDays(checkInDate, 1) : today}
                  value={checkOutDate}
                  onChange={(event) => setCheckOutDate(event.target.value)}
                />
              </div>
              <div className="home-search__field home-search__field--guests">
                <label htmlFor="home-search-guests">Số khách</label>
                <input
                  id="home-search-guests"
                  type="number"
                  min={1}
                  step={1}
                  value={guestCount}
                  onChange={(event) => setGuestCount(event.target.value)}
                />
              </div>
              <button className="home-search__submit" type="submit" disabled={isSearching}>
                {isSearching ? 'Đang tra phòng...' : (
                  <>
                    <svg viewBox="0 0 24 24" aria-hidden="true">
                      <circle cx="10.8" cy="10.8" r="6.3" />
                      <path d="m15.5 15.5 4.2 4.2" />
                    </svg>
                    Tra phòng
                  </>
                )}
              </button>
            </div>
            {(roomTypeLoadError || validationError) && (
              <p className={`home-search__message${validationError ? ' home-search__message--error' : ''}`}
                role={validationError ? 'alert' : 'status'}>
                {validationError || roomTypeLoadError}
              </p>
            )}
          </form>
          {hasSearched && (
            <section className="home-search-results" aria-labelledby="home-search-results-title" aria-live="polite">
              <div className="home-search-results__heading">
                <p className="home-search__eyebrow">KẾT QUẢ PHÒNG TRỐNG</p>
                {!isSearching && !searchError && (
                  <p className="home-search-results__count">
                    Tìm thấy {visibleResults.length} loại phòng phù hợp
                  </p>
                )}
              </div>
              {roomTypeCardsLoadError && (
                <p className="home-search-results__image-notice" role="status">{roomTypeCardsLoadError}</p>
              )}
              <h2 id="home-search-results-title" className="home-search-results__sr-only">
                Kết quả phòng trống
              </h2>
              {isSearching ? (
                <ul className="home-search-results__grid" aria-label="Đang tải kết quả">
                  {[1, 2, 3].map((item) => (
                    <li className="home-search-result home-search-result--skeleton" key={item}>
                      <div className="home-search-result__image" />
                      <div className="home-search-result__content">
                        <span />
                        <span />
                        <span />
                      </div>
                    </li>
                  ))}
                </ul>
              ) : searchError ? (
                <p className="home-search-results__state home-search-results__state--error" role="alert">
                  {searchError}
                </p>
              ) : visibleResults.length === 0 ? (
                <p className="home-search-results__state" role="status">
                  Không còn phòng phù hợp với thời gian và số khách đã chọn.
                </p>
              ) : (
                <ul className="home-search-results__grid">
                  {visibleResults.map((room) => {
                    const roomTypeCard = roomTypeCardById.get(room.roomTypeId)
                    return (
                      <li className="home-search-result" key={room.roomTypeId}>
                        <img
                          className="home-search-result__image"
                          src={roomTypeCard?.imageUrl || DEFAULT_ROOM_IMAGE}
                          alt={roomTypeCard?.imageAlt || `Ảnh ${room.name}`}
                          onError={(event) => {
                            event.currentTarget.onerror = null
                            event.currentTarget.src = DEFAULT_ROOM_IMAGE
                          }}
                        />
                        <div className="home-search-result__content">
                          <h3>{room.name}</h3>
                          <p>Sức chứa tối đa {room.capacity} khách</p>
                          <p className="home-search-result__price">
                            {money.format(room.price)} ₫ <span>/ đêm</span>
                          </p>
                          <p className="home-search-result__availability">Còn {room.availableRooms} phòng</p>
                          <a className="home-search-result__book" href={bookingHref(room.roomTypeId)}>
                            Đặt phòng
                          </a>
                        </div>
                      </li>
                    )
                  })}
                </ul>
              )}
            </section>
          )}
        </section>
      </main>
    </div>
  )
}
