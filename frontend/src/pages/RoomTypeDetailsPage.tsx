import { useEffect, useState } from 'react'
import { ApiRequestError } from '../services/apiClient'
import { getPublicRoomType } from '../services/guestBookingService'
import type { PublicRoomTypeDetails } from '../types/guestBooking'
import './AuthPages.css'
import './GuestBookingPage.css'
import './RoomTypeDetailsPage.css'

type RoomTypeDetailsPageProps = {
  roomTypeId: number | null
}

const money = new Intl.NumberFormat('vi-VN')

export function RoomTypeDetailsPage({ roomTypeId }: RoomTypeDetailsPageProps) {
  const [roomType, setRoomType] = useState<PublicRoomTypeDetails | null>(null)
  const [apiNotFound, setApiNotFound] = useState(false)
  const [error, setError] = useState('')
  const notFound = roomTypeId === null || apiNotFound

  useEffect(() => {
    if (roomTypeId === null) {
      return
    }
    getPublicRoomType(roomTypeId)
      .then(setRoomType)
      .catch((cause: unknown) => {
        if (cause instanceof ApiRequestError && cause.status === 404) {
          if (cause.message === 'Loại phòng này hiện đã ngừng bán') {
            setError(cause.message)
          } else {
            setApiNotFound(true)
          }
          return
        }
        setError(cause instanceof TypeError
          ? 'Hiện không thể kết nối để tải thông tin phòng. Vui lòng thử lại sau.'
          : cause instanceof Error
            ? cause.message
            : 'Không tải được thông tin loại phòng')
      })
  }, [roomTypeId])

  return (
    <main className="login-layout guest-booking room-type-details">
      <section className="brand-panel" aria-label="HomeStay">
        <div>
          <p className="eyebrow">HomeStay</p>
          <h1>Thông tin phòng.</h1>
          <p className="brand-copy">Xem chi tiết loại phòng trước khi tiếp tục đặt phòng.</p>
        </div>
        <div className="brand-footer">
          <span className="brand-mark" aria-hidden="true">H</span>
          <span>Đặt phòng trực tuyến</span>
        </div>
      </section>

      <section className="form-panel">
        {roomType ? (
          <article className="login-form room-type-details-card">
            <p className="eyebrow">Chi tiết loại phòng</p>
            <h2>{roomType.name}</h2>
            {roomType.description && <p className="form-intro">{roomType.description}</p>}
            <dl className="guest-booking-summary">
              <dt>Mã loại phòng</dt>
              <dd>{roomType.code}</dd>
              <dt>Sức chứa tiêu chuẩn</dt>
              <dd>{roomType.standardCapacity} khách</dd>
              <dt>Sức chứa tối đa</dt>
              <dd>{roomType.maxCapacity} khách</dd>
              <dt>Số giường</dt>
              <dd>{roomType.numberOfBeds}</dd>
              {roomType.weekdayPrice != null && (
                <>
                  <dt>Giá ngày thường</dt>
                  <dd>{money.format(roomType.weekdayPrice)} đ/đêm</dd>
                </>
              )}
              {roomType.weekendPrice != null && (
                <>
                  <dt>Giá cuối tuần</dt>
                  <dd>{money.format(roomType.weekendPrice)} đ/đêm</dd>
                </>
              )}
            </dl>
            <a className="submit-button room-type-details-action" href="/dat-phong">Đặt phòng</a>
          </article>
        ) : notFound ? (
          <section className="login-form room-type-unavailable" role="alert">
            <p className="eyebrow">Lỗi 404</p>
            <h2>Không tìm thấy loại phòng</h2>
            <p className="form-intro">
              Loại phòng bạn đang tìm có thể đã bị xóa hoặc đường dẫn không chính xác.
            </p>
            <a className="submit-button room-type-details-action" href="/dat-phong">
              Quay lại tìm phòng
            </a>
          </section>
        ) : error ? (
          <section className="login-form room-type-unavailable" role="alert">
            <p className="eyebrow">{error === 'Loại phòng này hiện đã ngừng bán' ? 'Lỗi 404' : 'Thông báo'}</p>
            <h2>{error === 'Loại phòng này hiện đã ngừng bán' ? 'Loại phòng đã ngừng bán' : 'Không tải được thông tin phòng'}</h2>
            <p className="form-intro">{error}</p>
            <a className="submit-button room-type-details-action" href="/dat-phong">
              Tra phòng khác
            </a>
          </section>
        ) : (
          <section className="login-form" aria-busy="true" aria-live="polite">
            <p className="form-intro">Đang tải thông tin loại phòng...</p>
          </section>
        )}
      </section>
    </main>
  )
}
