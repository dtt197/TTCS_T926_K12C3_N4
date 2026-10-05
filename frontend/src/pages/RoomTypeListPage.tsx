import { useEffect, useState } from 'react'
import { getPublicRoomTypeCards } from '../services/publicRoomTypeService'
import type { PublicRoomTypeCard } from '../types/publicRoomType'
import './RoomTypeListPage.css'

const DEFAULT_COVER_IMAGE = '/room-images/room-1.jpg'
const HERO_IMAGE = '/assets/hero-room.jpg'

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

  useEffect(() => {
    let cancelled = false
    setIsLoading(true)
    setHasError(false)
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

  return (
    <div className="customer-home">
      <header className="customer-header">
        <div className="customer-header__inner">
          <a className="customer-brand" href="/danh-sach-loai-phong" aria-label="HomeStay - Trang chủ">
            <span className="customer-brand__mark" aria-hidden="true">
              <svg viewBox="0 0 40 40">
                <path d="m5.5 18 14.5-12L34.5 18" />
                <path d="M9.5 16.5V34h21V16.5M16 34V23h8v11" />
                <path d="M27 10.5V7h4v6.5" />
              </svg>
            </span>
            <span className="customer-brand__text">
              <strong>HomeStay</strong>
              <small>Nghỉ dưỡng như ở nhà</small>
            </span>
          </a>
          <nav className="customer-nav" aria-label="Điều hướng khách hàng">
            <a className="customer-nav__link" href="/danh-sach-loai-phong">Trang chủ</a>
            <a className="customer-nav__link customer-nav__link--active" href="#room-type-list">Các loại phòng</a>
            <a className="customer-nav__link" href="/tim-phong">Tra phòng trống</a>
            <a className="customer-nav__link" href="/tra-cuu-booking">Tra cứu booking</a>
          </nav>
          <div className="customer-header__contact">
            <a href="tel:0389123456">0389 123 456</a>
            <small>Hỗ trợ 8:00 - 22:00</small>
          </div>
          <a className="customer-header__admin" href="/">Quản trị viên</a>
        </div>
      </header>

      <main>
        <section className="customer-hero" aria-labelledby="customer-hero-title">
          <div className="customer-hero__copy">
            <p className="customer-eyebrow">HOMESTAY</p>
            <h1 className="customer-hero__title" id="customer-hero-title">
              Tìm kỳ nghỉ<br />phù hợp với bạn
            </h1>
            <p className="customer-hero__description">
              Không gian ấm cúng, tiện nghi hiện đại, gần gũi thiên nhiên.
              Trải nghiệm homestay thoải mái và đáng nhớ.
            </p>
            <div className="customer-hero__actions">
              <a className="customer-button customer-button--primary" href="/tim-phong">
                Tra phòng trống
              </a>
              <a className="customer-button customer-button--secondary" href="#room-type-list">
                Xem các loại phòng
              </a>
            </div>
            <a className="customer-hero__booking-link" href="/tra-cuu-booking">
              Tra cứu booking
            </a>
            <ul className="customer-benefits" aria-label="Điểm nổi bật">
              <li className="customer-benefit">
                <span className="customer-benefit__icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24"><path d="M19.5 4.5C12 4.5 6 7.3 6 13a5.5 5.5 0 0 0 5.5 5.5c5.7 0 8-6.5 8-14Z" /><path d="M4 20c2.5-5 6.5-8 12-11" /></svg>
                </span>
                <span className="customer-benefit__text">
                  <strong>Không gian xanh</strong>
                  <small>Gần gũi thiên nhiên</small>
                </span>
              </li>
              <li className="customer-benefit">
                <span className="customer-benefit__icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24"><path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-6v-7h-4v7H4a1 1 0 0 1-1-1V10Z" /><path d="M8 10h.01M16 10h.01" /></svg>
                </span>
                <span className="customer-benefit__text">
                  <strong>Tiện nghi đầy đủ</strong>
                  <small>Thoải mái như ở nhà</small>
                </span>
              </li>
              <li className="customer-benefit">
                <span className="customer-benefit__icon" aria-hidden="true">
                  <svg viewBox="0 0 24 24"><circle cx="9" cy="8" r="3" /><path d="M3.5 20a5.5 5.5 0 0 1 11 0M16 5.5a3 3 0 0 1 0 5.8M17 14a4.5 4.5 0 0 1 3.5 4.4" /></svg>
                </span>
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
              <span className="customer-hero__info-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <path d="M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1 1 14 0Z" />
                  <circle cx="12" cy="10" r="2.25" />
                </svg>
              </span>
              <span className="customer-hero__info-text">
                <strong>Không gian yên bình</strong>
                <small>Trải nghiệm trọn vẹn tại HomeStay</small>
              </span>
            </div>
          </div>
        </section>

        <section className="room-section" id="room-type-list" aria-labelledby="room-section-title">
          <div className="room-section__inner">
            <div className="room-section__heading">
              <div className="room-section__intro">
                <p className="customer-eyebrow">KHÔNG GIAN LƯU TRÚ</p>
                <h2 className="room-section__title" id="room-section-title">Các loại phòng</h2>
                <p className="room-section__description">
                  Chọn loại phòng phù hợp với nhu cầu và ngân sách của bạn.
                </p>
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
                <button type="button" onClick={() => setRetryCount((count) => count + 1)}>
                  Thử lại
                </button>
              </div>
            ) : cards.length === 0 ? (
              <p className="room-section__state" role="status">Hiện chưa có loại phòng nào.</p>
            ) : (
              <ul className="room-grid">
                {cards.map((card) => (
                  <li key={card.id} className="room-card">
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
                    <div className="room-card__body">
                      <h3 className="room-card__name">{card.name}</h3>
                      <p className="room-card__capacity">{formatCapacity(card)}</p>
                      <p className="room-card__price">{formatPrice(card.fromPrice)}</p>
                      <a className="room-card__details" href={`/loai-phong/${card.id}`}>
                        Xem chi tiết
                      </a>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>
      </main>
    </div>
  )
}