import { useEffect, useState, type FormEvent, type ReactNode } from 'react'
import { PublicHeader } from '../components/PublicHeader'
import { PublicHero } from '../components/PublicHero'
import { getPublicRoomTypes, getPublicRoomTypesCatalog } from '../services/guestBookingService'
import { getPublicRoomTypeCards } from '../services/publicRoomTypeService'
import { getSettings } from '../services/settingsService'
import { searchAvailableRooms, type RoomAvailabilityResponse } from '../services/roomService'
import type { PublicRoomTypeOption } from '../types/guestBooking'
import type { PublicRoomTypeCard } from '../types/publicRoomType'
import type { PublicRoomTypeDetail } from '../types/roomDetail'
import type { OperatingSettings } from '../types/settings'
import './RoomTypeListPage.css'
import './HomePage.css'

const money = new Intl.NumberFormat('vi-VN')
const API_ORIGIN = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '')
const DECORATIVE_IMAGE = 'https://images.unsplash.com/photo-1707064148934-0e09c2fb32b2?auto=format&fit=crop&w=1500&q=85'

function roomImageUrl(value: string | null | undefined): string | null {
  if (!value?.trim()) return null
  const url = value.trim()
  if (/^\/room-images\/room-[1-4]\.jpg(?:[?#].*)?$/i.test(url)) return null
  if (/^(https?:|data:|blob:|\/\/)/i.test(url)) return url.startsWith('//') ? `${window.location.protocol}${url}` : url
  return url.startsWith('/uploads/') ? `${API_ORIGIN}${url}` : url
}

function ImageFallback({ label, hidden = false }: { label: string; hidden?: boolean }) {
  return <div className="home-image-fallback" aria-label={label} hidden={hidden}><svg viewBox="0 0 48 48" aria-hidden="true"><path d="M7 38V13l17-8 17 8v25M3 38h42M15 38V25h18v13M14 16h.01M24 16h.01M34 16h.01" /></svg><span>{label}</span></div>
}

function AmenityIcon({ icon }: { icon: ReactNode }) {
  return <svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">{icon}</svg>
}

function amenityIcon(code: string, name: string): ReactNode {
  const key = `${code} ${name}`.toLocaleLowerCase('vi')
  if (/wifi|wi-fi|internet/.test(key)) return <><path d="M5 9a11 11 0 0 1 14 0"/><path d="M8 12a6.5 6.5 0 0 1 8 0"/><path d="M11 15a2 2 0 0 1 2 0"/><path d="M12 19h.01"/></>
  if (/giuong|giường|bed/.test(key)) return <><path d="M3 18V7m0 7h18v4M3 11h5a3 3 0 0 1 3 3H3z"/><path d="M11 14h10"/></>
  if (/tam|tắm|bath|phong ve sinh|phòng vệ sinh/.test(key)) return <><path d="M4 12h16v3a5 5 0 0 1-5 5H9a5 5 0 0 1-5-5z"/><path d="M7 12V7a3 3 0 0 1 6 0v1"/></>
  if (/coffee|cafe|cà phê|nuoc|nước|bep|bếp/.test(key)) return <><path d="M5 9h12v6a4 4 0 0 1-4 4H9a4 4 0 0 1-4-4z"/><path d="M17 11h2a2 2 0 1 1 0 4h-2M8 5c-1-1 1-2 0-3m5 3c-1-1 1-2 0-3"/></>
  if (/dieu hoa|điều hòa|may lanh|máy lạnh|air/.test(key)) return <><path d="M12 3v18m-7-14 14 8m0-8L5 15"/><path d="m9 5 3-2 3 2m-6 14 3 2 3-2"/></>
  if (/xe|parking|do xe|đỗ xe/.test(key)) return <><path d="M6 21V4h7a5 5 0 0 1 0 10H6"/><path d="M4 21h16"/></>
  if (/cay|cây|vuon|vườn|green/.test(key)) return <><path d="M20 4C10 4 5 8 5 14a6 6 0 0 0 6 6c6 0 9-7 9-16Z"/><path d="M3 21c3-5 7-8 13-12"/></>
  return <><path d="m12 3 2.3 5.1 5.6.7-4.1 3.9 1.1 5.5-4.9-2.8-4.9 2.8 1.1-5.5-4.1-3.9 5.6-.7z"/></>
}

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
  const [roomDetails, setRoomDetails] = useState<PublicRoomTypeDetail[]>([])
  const [settings, setSettings] = useState<OperatingSettings | null>(null)
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

    getPublicRoomTypesCatalog()
      .then((data) => {
        if (!cancelled) setRoomDetails(data)
      })
      .catch(() => undefined)

    getSettings()
      .then((data) => {
        if (!cancelled) setSettings(data)
      })
      .catch(() => undefined)
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
  const featuredRooms = roomTypeCards.slice(0, 3)
  const hasContactDetails = Boolean(settings?.address || settings?.phone || settings?.email)
  const featuredAmenities = Array.from(new Map(
    roomDetails.flatMap((detail) => detail.amenities.map((amenity) => [amenity.id, amenity] as const)),
  ).values()).slice(0, 8)
  const galleryUrls = new Set<string>()
  const galleryImages = roomTypeCards.flatMap((card) => {
    const src = roomImageUrl(card.imageUrl)
    if (!src || galleryUrls.has(src)) return []
    galleryUrls.add(src)
    return [{ src, alt: card.imageAlt || `Ảnh ${card.name}`, key: card.id, name: card.name }]
  }).slice(0, 5)

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
        <PublicHero titleId="home-hero-title" />

        <section className="home-search" id="tim-phong" aria-label="Tìm phòng">
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
                    const image = roomImageUrl(roomTypeCard?.imageUrl)
                    return (
                      <li className="home-search-result" key={room.roomTypeId}>
                        {image && <img
                          className="home-search-result__image"
                          src={image}
                          alt={roomTypeCard?.imageAlt || `Ảnh ${room.name}`}
                          onError={(event) => {
                            event.currentTarget.hidden = true
                            event.currentTarget.nextElementSibling?.removeAttribute('hidden')
                          }}
                        />}
                        <ImageFallback label="Ảnh phòng chưa được cập nhật" hidden={Boolean(image)} />
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

        <section className="landing-section landing-about" id="gioi-thieu" aria-labelledby="landing-about-title">
          <div className="landing-about__image-wrap">
            <img src={DECORATIVE_IMAGE} alt="Lối vào lưu trú với cây xanh, ảnh tham khảo phong cách nghỉ dưỡng" loading="lazy" onError={(event) => { event.currentTarget.hidden = true; event.currentTarget.nextElementSibling?.removeAttribute('hidden') }} />
            <ImageFallback label="Một khoảng xanh để nghỉ ngơi" hidden />
            <div className="landing-about__image-note"><span>CHẬM LẠI MỘT NHỊP</span><strong>Chốn nghỉ của riêng bạn</strong></div>
          </div>
          <div className="landing-about__copy">
            <p className="landing-kicker">MỘT KHOẢNG DỪNG BÌNH YÊN</p>
            <h2 id="landing-about-title">Một kỳ nghỉ gần gũi thiên nhiên</h2>
            <p>{settings?.homestayName ? `${settings.homestayName} mở ra một nhịp nghỉ chậm rãi, nơi ánh sáng tự nhiên và tiện nghi vừa đủ giúp bạn thấy dễ chịu như ở nhà.` : 'Tìm một nhịp nghỉ chậm hơn trong không gian lưu trú ấm áp. Chọn căn phòng phù hợp, xem thông tin rõ ràng và chủ động lên kế hoạch cho chuyến đi.'}</p>
            <a className="landing-text-link" href="#phong-noi-bat">Khám phá không gian lưu trú <span aria-hidden="true">↗</span></a>
          </div>
        </section>

        <section className="landing-section landing-why" aria-labelledby="landing-why-title">
          <div className="landing-section__heading"><p className="landing-kicker">TRẢI NGHIỆM HOMEStay</p><h2 id="landing-why-title">Vì sao chọn HomeStay?</h2><p>Mọi điều cần thiết để bạn dễ dàng bắt đầu kế hoạch nghỉ ngơi.</p></div>
          <div className="landing-why__grid">
            {[
              { title: 'Không gian gần gũi', copy: 'Thiết kế ấm áp, tạo cảm giác thư thái như ở nhà.', icon: <><path d="M3 11 12 4l9 7"/><path d="M5 10v10h14V10M9 20v-6h6v6"/></> },
              { title: 'Chọn phòng dễ dàng', copy: 'Xem sức chứa, giá tham khảo và thông tin từng loại phòng.', icon: <><rect x="4" y="5" width="16" height="15" rx="2"/><path d="M8 3v4m8-4v4M4 10h16"/></> },
              { title: 'Thông tin minh bạch', copy: 'Chi tiết phòng và tiện nghi được cập nhật từ hệ thống.', icon: <><path d="M12 3 5 6v5c0 4.5 2.8 7.8 7 10 4.2-2.2 7-5.5 7-10V6z"/><path d="m9 12 2 2 4-4"/></> },
              { title: 'Đặt phòng thuận tiện', copy: 'Tìm phòng trống và gửi yêu cầu đặt phòng trực tuyến.', icon: <><rect x="4" y="5" width="16" height="15" rx="2"/><path d="M8 3v4m8-4v4M4 10h16m5 3v4m-2-2h4"/></> },
            ].map(({ icon, title, copy }) => <article className="landing-why__card" key={title}><span className="landing-why__icon" aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">{icon}</svg></span><h3>{title}</h3><p>{copy}</p></article>)}
          </div>
        </section>

        <section className="landing-section landing-rooms" id="phong-noi-bat" aria-labelledby="landing-rooms-title">
          <div className="landing-section__heading landing-section__heading--row"><div><p className="landing-kicker">LỰA CHỌN DÀNH CHO BẠN</p><h2 id="landing-rooms-title">Các loại phòng nổi bật</h2><p>Tên, sức chứa, giá và ảnh lấy từ danh mục phòng hiện có.</p></div><a className="landing-text-link" href="/danh-sach-loai-phong">Xem tất cả phòng <span aria-hidden="true">↗</span></a></div>
          {isLoadingRoomTypes ? <p className="landing-state" role="status">Đang tải các loại phòng...</p> : roomTypeCardsLoadError ? <div className="landing-state landing-state--error" role="alert">Chưa thể tải danh sách phòng. Vui lòng thử lại sau hoặc xem <a href="/danh-sach-loai-phong">trang danh sách phòng</a>.</div> : featuredRooms.length === 0 ? <p className="landing-state" role="status">Hiện chưa có loại phòng để giới thiệu.</p> : <div className="landing-room-grid">{featuredRooms.map((room) => {
            const image = roomImageUrl(room.imageUrl)
            return <article className="landing-room-card" key={room.id}><a className="landing-room-card__media" href={`/loai-phong/${room.id}`} aria-label={`Xem chi tiết ${room.name}`}>{image && <img src={image} alt={room.imageAlt || `Ảnh ${room.name}`} loading="lazy" onError={(event) => { event.currentTarget.hidden = true; event.currentTarget.nextElementSibling?.removeAttribute('hidden') }} />}<ImageFallback label="Ảnh thực tế chưa được cập nhật" hidden={Boolean(image)} /></a><div className="landing-room-card__body"><p className="landing-kicker">LOẠI PHÒNG</p><h3>{room.name}</h3><p className="landing-room-card__capacity">Sức chứa {room.standardCapacity}{room.maxCapacity > room.standardCapacity ? `–${room.maxCapacity}` : ''} khách</p><div className="landing-room-card__bottom"><strong>{room.fromPrice === null ? 'Liên hệ để biết giá' : <>Từ {money.format(room.fromPrice)} ₫<small>/ đêm</small></>}</strong><a href={`/loai-phong/${room.id}`}>Xem chi tiết <span aria-hidden="true">→</span></a></div></div></article>
          })}</div>}
        </section>

        <section className="landing-section landing-amenities" id="tien-nghi" aria-labelledby="landing-amenities-title"><div className="landing-section__heading"><p className="landing-kicker">TIỆN NGHI THEO DANH MỤC PHÒNG</p><h2 id="landing-amenities-title">Tiện nghi nổi bật</h2><p>Chỉ hiển thị tiện nghi đã khai báo trong thông tin phòng.</p></div>{featuredAmenities.length ? <ul className="landing-amenity-list">{featuredAmenities.map((amenity) => <li key={amenity.id}><span className="landing-amenity-list__icon"><AmenityIcon icon={amenityIcon(amenity.code, amenity.name)} /></span><span>{amenity.name}</span></li>)}</ul> : <p className="landing-state">{roomDetails.length ? 'Chưa có tiện nghi được khai báo cho các loại phòng.' : 'Thông tin tiện nghi hiện chưa khả dụng.'}</p>}</section>

        <section className="landing-section landing-gallery" aria-labelledby="landing-gallery-title"><div className="landing-section__heading landing-section__heading--row"><div><p className="landing-kicker">GÓC NHÌN TỪ DANH MỤC PHÒNG</p><h2 id="landing-gallery-title">Thư viện ảnh</h2></div><a className="landing-text-link" href="/danh-sach-loai-phong">Xem không gian phòng <span aria-hidden="true">↗</span></a></div>{galleryImages.length ? <div className="landing-gallery__grid">{galleryImages.map((item, index) => <a className={`landing-gallery__item landing-gallery__item--${index + 1}`} href={`/loai-phong/${item.key}`} key={item.key} aria-label={`Xem chi tiết ${item.name}`}><img src={item.src} alt={item.alt} loading="lazy" onError={(event) => { event.currentTarget.closest('a')?.remove() }} /><span>{item.name}<b aria-hidden="true">↗</b></span></a>)}</div> : <div className="landing-gallery__fallback"><div className="landing-gallery__fallback-image" aria-hidden="true" /><p>Ảnh phòng thực tế sẽ xuất hiện tại đây khi được cập nhật vào danh mục.</p></div>}</section>

        <section className="landing-section landing-steps" aria-labelledby="landing-steps-title"><div className="landing-section__heading"><p className="landing-kicker">DỄ DÀNG BẮT ĐẦU</p><h2 id="landing-steps-title">Quy trình đặt phòng</h2></div><ol className="landing-steps__grid">{[['01', 'Chọn thời gian', 'Nhập ngày nhận, ngày trả và số khách.'], ['02', 'Tìm phòng phù hợp', 'Xem kết quả phòng còn trống theo nhu cầu.'], ['03', 'Gửi thông tin đặt', 'Điền thông tin khách lưu trú để tạo yêu cầu.'], ['04', 'Tra cứu xác nhận', 'Theo dõi tình trạng bằng mã đặt phòng và email.']].map(([number, title, copy]) => <li key={number}><span>{number}</span><h3>{title}</h3><p>{copy}</p></li>)}</ol><a className="landing-steps__cta" href="#tim-phong">Tìm phòng cho kỳ nghỉ <span aria-hidden="true">→</span></a></section>

        <section className="landing-contact" id="lien-he" aria-labelledby="landing-contact-title"><div><p className="landing-kicker">HOMESTAY LUÔN SẴN SÀNG HỖ TRỢ</p><h2 id="landing-contact-title">Bắt đầu lên kế hoạch nghỉ ngơi</h2><p>Chọn phòng phù hợp hoặc liên hệ với chúng tôi để được hỗ trợ thêm.</p></div>{(settings?.phone || settings?.email) && <div className="landing-contact__actions">{settings.phone && <a href={`tel:${settings.phone}`}>Gọi {settings.phone}</a>}{settings.email && <a href={`mailto:${settings.email}`}>Gửi email</a>}</div>}</section>
        <footer className={`landing-footer${hasContactDetails ? ' landing-footer--with-contact' : ''}`}><div className="landing-footer__brand-block"><a className="landing-footer__brand" href="/trang-chu">{settings?.homestayName || 'HomeStay'}</a><p>Một chốn nghỉ ấm áp để bạn trở về với nhịp sống chậm rãi và những điều giản dị.</p></div><nav className="landing-footer__links" aria-label="Liên kết nhanh"><strong>Khám phá</strong><a href="#gioi-thieu">Giới thiệu</a><a href="#phong-noi-bat">Các loại phòng</a><a href="#tien-nghi">Tiện nghi</a><a href="#tim-phong">Tìm phòng</a></nav>{hasContactDetails && <div className="landing-footer__contact"><strong>Liên hệ</strong>{settings?.address && <span>{settings.address}</span>}{settings?.phone && <a href={`tel:${settings.phone}`}>{settings.phone}</a>}{settings?.email && <a href={`mailto:${settings.email}`}>{settings.email}</a>}</div>}<div className="landing-footer__bottom"><span>© {new Date().getFullYear()} {settings?.homestayName || 'HomeStay'}</span><a href="/tra-cuu-dat-phong">Tra cứu đặt phòng</a></div></footer>
      </main>
    </div>
  )
}
