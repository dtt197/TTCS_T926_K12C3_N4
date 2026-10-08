import { useEffect, useState } from 'react'
import { getPublicRoomTypesCatalog } from '../services/guestBookingService'
import { getPublicRoomTypeCards } from '../services/publicRoomTypeService'
import { PublicHeader } from '../components/PublicHeader'
import { PublicHero } from '../components/PublicHero'
import type { PublicRoomTypeCard } from '../types/publicRoomType'
import type { PublicRoomTypeDetail } from '../types/roomDetail'
import './RoomTypeListPage.css'

/** Số tiện nghi hiện trên thẻ, phần còn lại gộp thành "+N tiện nghi khác". */
const MAX_CARD_AMENITIES = 4

const DEFAULT_COVER_IMAGE = '/room-images/room-1.jpg'
function formatPrice(price: number | null): string {
  if (price === null) {
    return 'Liên hệ để biết giá'
  }
  return `Từ ${new Intl.NumberFormat('vi-VN').format(price)} ₫ / đêm`
}

function formatCapacity(card: PublicRoomTypeCard): string {
  if (card.maxCapacity > card.standardCapacity) {
    return `${card.standardCapacity} khách (tối đa ${card.maxCapacity})`
  }
  return `${card.standardCapacity} khách`
}

export function RoomTypeListPage() {
  const [cards, setCards] = useState<PublicRoomTypeCard[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [hasError, setHasError] = useState(false)
  const [retryCount, setRetryCount] = useState(0)
  // Chuyển từ trang /loai-phong cũ: số phòng còn, số giường, mô tả, tiện nghi và ô tìm kiếm
  const [details, setDetails] = useState<Record<number, PublicRoomTypeDetail>>({})
  const [searchTerm, setSearchTerm] = useState('')

  useEffect(() => {
    let cancelled = false
    // Không tải được phần thêm thì thẻ vẫn hiện ảnh, tên, sức chứa và giá như cũ
    getPublicRoomTypesCatalog()
      .then((data) => {
        if (!cancelled) {
          setDetails(Object.fromEntries(data.map((detail) => [detail.id, detail])))
        }
      })
      .catch(() => undefined)
    getPublicRoomTypeCards()
      .then((data) => {
        if (!cancelled) {
          setCards(data)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setHasError(true)
        }
      })
      .finally(() => {
        if (!cancelled) {
          setIsLoading(false)
        }
      })
    return () => {
      cancelled = true
    }
  }, [retryCount])

  const term = searchTerm.trim().toLowerCase()
  const visibleCards = term
    ? cards.filter((card) => {
        const detail = details[card.id]
        return [
          card.name,
          detail?.code,
          detail?.description,
          ...(detail?.amenities ?? []).map((amenity) => amenity.name),
        ].some((text) => text?.toLowerCase().includes(term))
      })
    : cards

  return (
    <div className="customer-home">
      <PublicHeader />

      <main>
        <PublicHero titleId="customer-hero-title" />

        <section className="room-section" id="room-type-list" aria-labelledby="room-section-title">
          <div className="room-section__inner">
            <div className="room-section__heading">
              <div className="room-section__intro">
                <p className="customer-eyebrow">KHÔNG GIAN LƯU TRÚ</p>
                <h2 className="room-section__title" id="room-section-title">Các loại phòng</h2>
                <p className="room-section__description">
                  Chọn loại phòng phù hợp với nhu cầu và ngân sách của bạn.
                </p>
                <div className="room-search">
                  <input
                    className="room-search__input"
                    type="search"
                    placeholder="Tìm kiếm loại phòng theo tên, tiện nghi..."
                    aria-label="Tìm kiếm loại phòng theo tên, tiện nghi"
                    value={searchTerm}
                    onChange={(event) => setSearchTerm(event.target.value)}
                  />
                </div>
              </div>
              <ul className="room-trust-list" aria-label="Cam kết lưu trú">
                <li className="room-trust-item">
                  <span className="room-trust-item__icon" aria-hidden="true">
                    <svg viewBox="0 0 24 24">
                      <path d="M5 9h14l-1.2 10H6.2L5 9Z" />
                      <path d="M8 9V6.5a4 4 0 0 1 8 0V9M8 13h8" />
                    </svg>
                  </span>
                  <span className="room-trust-item__text">
                    <strong>Không gian ấm cúng</strong>
                    <small>Như ở nhà</small>
                  </span>
                </li>
                <li className="room-trust-item">
                  <span className="room-trust-item__icon" aria-hidden="true">
                    <svg viewBox="0 0 24 24">
                      <path d="M12 3 19 6v5c0 4.5-2.9 7.8-7 10-4.1-2.2-7-5.5-7-10V6l7-3Z" />
                      <path d="m9 12 2 2 4-4" />
                    </svg>
                  </span>
                  <span className="room-trust-item__text">
                    <strong>Sạch sẽ, tiện nghi</strong>
                    <small>Luôn được chăm sóc</small>
                  </span>
                </li>
                <li className="room-trust-item">
                  <span className="room-trust-item__icon" aria-hidden="true">
                    <svg viewBox="0 0 24 24">
                      <path d="M12 21s7-6.1 7-12a7 7 0 1 0-14 0c0 5.9 7 12 7 12Z" />
                      <circle cx="12" cy="9" r="2.3" />
                    </svg>
                  </span>
                  <span className="room-trust-item__text">
                    <strong>Vị trí thuận tiện</strong>
                    <small>Gần điểm tham quan</small>
                  </span>
                </li>
              </ul>
            </div>

            {isLoading ? (
              <p className="room-section__state" role="status">Đang tải danh sách loại phòng...</p>
            ) : hasError ? (
              <div className="room-section__state room-section__state--error" role="alert">
                <p>Không thể tải danh sách loại phòng. Vui lòng thử lại.</p>
                <button
                  type="button"
                  onClick={() => {
                    setIsLoading(true)
                    setHasError(false)
                    setRetryCount((count) => count + 1)
                  }}
                >
                  Thử lại
                </button>
              </div>
            ) : cards.length === 0 ? (
              <p className="room-section__state" role="status">Hiện chưa có loại phòng nào.</p>
            ) : visibleCards.length === 0 ? (
              <div className="room-section__state" role="status">
                <p>Không tìm thấy loại phòng nào phù hợp với từ khóa của bạn.</p>
                <button type="button" className="room-search__clear" onClick={() => setSearchTerm('')}>
                  Xóa từ khóa tìm kiếm
                </button>
              </div>
            ) : (
              <ul className="room-grid">
                {visibleCards.map((card) => {
                  const detail = details[card.id]
                  const amenities = detail?.amenities ?? []
                  return (
                    <li key={card.id} className="room-card">
                      <div className="room-card__media">
                        <img
                          className="room-card__image"
                          src={card.imageUrl || DEFAULT_COVER_IMAGE}
                          alt={card.imageAlt || `Ảnh ${card.name}`}
                          loading="lazy"
                          decoding="async"
                          onError={(event) => {
                            event.currentTarget.onerror = null
                            event.currentTarget.src = DEFAULT_COVER_IMAGE
                          }}
                        />
                        {detail && (
                          <span
                            className={`room-card__stock ${
                              detail.availableRooms <= 0
                                ? 'room-card__stock--out'
                                : detail.availableRooms <= 2
                                  ? 'room-card__stock--low'
                                  : ''
                            }`}
                            title="Số phòng còn trống cho đêm nay"
                          >
                            {detail.availableRooms <= 0 ? 'Đã hết phòng' : `Còn ${detail.availableRooms} phòng`}
                          </span>
                        )}
                      </div>
                      <div className="room-card__body">
                        <h3 className="room-card__name">{card.name}</h3>
                        <p className="room-card__capacity">
                          {formatCapacity(card)}
                          {detail && ` · ${detail.numberOfBeds} giường`}
                        </p>
                        {detail?.description && <p className="room-card__desc">{detail.description}</p>}
                        {amenities.length > 0 && (
                          <ul className="room-card__amenities" aria-label="Tiện nghi">
                            {amenities.slice(0, MAX_CARD_AMENITIES).map((amenity) => (
                              <li key={amenity.id} className="room-card__amenity">
                                {amenity.icon} {amenity.name}
                              </li>
                            ))}
                            {amenities.length > MAX_CARD_AMENITIES && (
                              <li className="room-card__amenity room-card__amenity--more">
                                +{amenities.length - MAX_CARD_AMENITIES} tiện nghi khác
                              </li>
                            )}
                          </ul>
                        )}
                        <p className="room-card__price">{formatPrice(card.fromPrice)}</p>
                        <div className="room-card__actions">
                          <a className="room-card__details" href={`/loai-phong/${card.id}`}>
                            Xem chi tiết
                          </a>
                          {card.activeRoomCount > 0 ? (
                            <a className="room-card__book" href={`/dat-phong?roomTypeId=${card.id}`}>
                              Đặt phòng
                            </a>
                          ) : (
                            <button type="button" className="room-card__book" disabled>
                              Hết phòng
                            </button>
                          )}
                        </div>
                      </div>
                    </li>
                  )
                })}
              </ul>
            )}
          </div>
        </section>
      </main>
    </div>
  )
}
