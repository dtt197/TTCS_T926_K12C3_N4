import { useEffect, useState } from 'react'
import { getPublicRoomTypeCards } from '../services/publicRoomTypeService'
import type { PublicRoomTypeCard } from '../types/publicRoomType'
import './RoomTypeListPage.css'

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

  useEffect(() => {
    let cancelled = false
    getPublicRoomTypeCards()
      .then((data) => {
        if (!cancelled) {
          setCards(data)
        }
      })
      .catch(() => {
        if (!cancelled) {
          setCards([])
        }
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <main className="room-list">
      <h1 className="room-list__title">Các loại phòng</h1>
      <ul className="room-list__grid">
        {cards.map((card) => (
          <li key={card.id} className="room-card">
            {card.imageUrl ? (
              <img
                className="room-card__image"
                src={card.imageUrl}
                alt={card.imageAlt}
              />
            ) : (
              <div
                className="room-card__image room-card__image--empty"
                role="img"
                aria-label={card.imageAlt}
              />
            )}
            <div className="room-card__body">
              <h2 className="room-card__name">{card.name}</h2>
              <p className="room-card__capacity">{formatCapacity(card)}</p>
              <p className="room-card__price">{formatPrice(card.fromPrice)}</p>
            </div>
          </li>
        ))}
      </ul>
    </main>
  )
}