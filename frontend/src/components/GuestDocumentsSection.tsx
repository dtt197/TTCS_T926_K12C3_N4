import { useEffect, useState } from 'react'
import { getGuestDocuments, revealGuestIdentity, type GuestDocument } from '../services/guestDocumentService'
import './GuestDocumentsSection.css'

type GuestDocumentsSectionProps = {
  bookingId: number
  canViewFull: boolean
}

/**
 * Khách lưu trú và giấy tờ: mặc định chỉ hiện 4 số cuối; Lễ tân/Chủ homestay bấm "Xem đầy đủ"
 * thì máy chủ trả số đầy đủ và ghi nhật ký (bảo vệ dữ liệu cá nhân, S1-05).
 */
export function GuestDocumentsSection({ bookingId, canViewFull }: GuestDocumentsSectionProps) {
  const [guests, setGuests] = useState<GuestDocument[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [revealed, setRevealed] = useState<Record<number, string>>({})
  const [revealing, setRevealing] = useState<number | null>(null)
  const [revealError, setRevealError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    getGuestDocuments(bookingId)
      .then((result) => {
        if (!cancelled) setGuests(result)
      })
      .catch((err) => {
        if (!cancelled) setLoadError(err instanceof Error ? err.message : 'Không tải được danh sách khách lưu trú.')
      })
    return () => {
      cancelled = true
    }
  }, [bookingId])

  async function handleReveal(guestId: number) {
    setRevealing(guestId)
    setRevealError(null)
    try {
      const identity = await revealGuestIdentity(bookingId, guestId)
      setRevealed((current) => ({ ...current, [guestId]: identity.identityNumber }))
    } catch (err) {
      setRevealError(err instanceof Error ? err.message : 'Không xem được số giấy tờ.')
    } finally {
      setRevealing(null)
    }
  }

  function handleHide(guestId: number) {
    setRevealed((current) => {
      const next = { ...current }
      delete next[guestId]
      return next
    })
  }

  if (loadError) {
    return (
      <section className="booking-detail-section booking-detail-full" aria-labelledby="booking-guests-title">
        <h4 id="booking-guests-title">Khách lưu trú</h4>
        <p className="guest-documents-error">{loadError}</p>
      </section>
    )
  }
  if (!guests || guests.length === 0) {
    return null
  }

  return (
    <section className="booking-detail-section booking-detail-full" aria-labelledby="booking-guests-title">
      <h4 id="booking-guests-title">Khách lưu trú</h4>
      <ul className="booking-guest-list">
        {guests.map((guest) => {
          const fullNumber = revealed[guest.guestId]
          return (
            <li key={guest.guestId} className="guest-documents-item">
              <span>{guest.fullName}{guest.primary ? ' (khách chính)' : ''}</span>
              {guest.hasIdentityNumber && (
                <span className="guest-documents-id">
                  Giấy tờ: <strong>{fullNumber ?? guest.maskedIdentityNumber}</strong>
                  {canViewFull && (fullNumber ? (
                    <button type="button" className="guest-documents-toggle" onClick={() => handleHide(guest.guestId)}>
                      Ẩn
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="guest-documents-toggle"
                      disabled={revealing === guest.guestId}
                      onClick={() => handleReveal(guest.guestId)}
                    >
                      {revealing === guest.guestId ? 'Đang tải...' : 'Xem đầy đủ'}
                    </button>
                  ))}
                </span>
              )}
            </li>
          )
        })}
      </ul>
      {canViewFull && <p className="guest-documents-note">Mỗi lần xem đầy đủ số giấy tờ đều được ghi vào nhật ký hệ thống.</p>}
      {revealError && <p className="guest-documents-error">{revealError}</p>}
    </section>
  )
}
