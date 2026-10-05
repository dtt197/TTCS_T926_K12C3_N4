import { useEffect, useState } from 'react'
import { getPublicRoomTypeCards } from '../services/publicRoomTypeService'
import type { PublicRoomTypeCard } from '../types/publicRoomType'
import './RoomTypeListPage.css'

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
    <main className="room-list">
      <h1 className="room-list__title">Các loại phòng</h1>
      {isLoading ? (
        <p className="room-list__state" role="status">Đang tải danh sách loại phòng...</p>
      ) : hasError ? (
        <div className="room-list__state room-list__state--error" role="alert">
          <p>Không thể tải danh sách loại phòng. Vui lòng thử lại.</p>
          <button type="button" onClick={() => setRetryCount((count) => count + 1)}>
            Thử lại
          </button>
        </div>
      ) : cards.length === 0 ? (
        <p className="room-list__state" role="status">Hiện chưa có loại phòng nào.</p>
      ) : (
        <ul className="room-list__grid">
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
                <h2 className="room-card__name">{card.name}</h2>
                <p className="room-card__capacity">{formatCapacity(card)}</p>
                <p className="room-card__price">{formatPrice(card.fromPrice)}</p>
                <a className="room-card__details-link" href={`/loai-phong/${card.id}`}>
                  Xem chi tiết
                </a>
              </div>
            </li>
          ))}
        </ul>
      )}
    </main>
  )
}