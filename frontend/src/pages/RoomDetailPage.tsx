import { useEffect, useState } from 'react'
import { getPublicRoomTypeDetail } from '../services/guestBookingService'
import type { PublicRoomTypeDetail } from '../types/roomDetail'
import './RoomDetailPage.css'

type RoomDetailPageProps = {
  roomTypeId?: number | string | null
  onBack?: () => void
  onBookNow?: (roomTypeId: number) => void
}

const money = new Intl.NumberFormat('vi-VN')

// Fallback images in case network image cannot be loaded
const FALLBACK_IMAGE =
  'https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80'

export function RoomDetailPage({
  roomTypeId: propRoomTypeId,
  onBack,
  onBookNow,
}: RoomDetailPageProps) {
  // Determine roomTypeId from props, or URL search param (?id=...), or URL path (/phong/:id)
  const [roomTypeId] = useState<string | number | null>(() => {
    if (propRoomTypeId) return propRoomTypeId
    const searchParams = new URLSearchParams(window.location.search)
    const idFromQuery = searchParams.get('id')
    if (idFromQuery) return idFromQuery
    const pathParts = window.location.pathname.split('/').filter(Boolean)
    const lastPart = pathParts[pathParts.length - 1]
    if (lastPart && /^\d+$/.test(lastPart)) return lastPart
    return null
  })

  const [roomType, setRoomType] = useState<PublicRoomTypeDetail | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [activeImageIndex, setActiveImageIndex] = useState(0)
  const [imageErrorMap, setImageErrorMap] = useState<Record<number, boolean>>({})

  useEffect(() => {
    if (!roomTypeId) {
      setError('Vui lòng chọn hoặc chỉ định mã loại phòng')
      setIsLoading(false)
      return
    }

    setIsLoading(true)
    setError(null)
    setActiveImageIndex(0)

    getPublicRoomTypeDetail(roomTypeId)
      .then((data) => {
        setRoomType(data)
        setIsLoading(false)
      })
      .catch((err: unknown) => {
        const msg =
          err instanceof Error
            ? err.message
            : 'Không tìm thấy thông tin loại phòng'
        setError(msg)
        setIsLoading(false)
      })
  }, [roomTypeId])

  const handleBackToList = () => {
    if (onBack) {
      onBack()
    } else {
      window.location.href = '/phong'
    }
  }

  const handleBooking = (id: number) => {
    if (onBookNow) {
      onBookNow(id)
    } else {
      window.location.href = `/dat-phong?roomTypeId=${id}`
    }
  }

  const galleryImages =
    roomType?.images && roomType.images.length > 0
      ? roomType.images
      : [FALLBACK_IMAGE]

  const nextImage = () => {
    setActiveImageIndex((prev) => (prev + 1) % galleryImages.length)
  }

  const prevImage = () => {
    setActiveImageIndex(
      (prev) => (prev - 1 + galleryImages.length) % galleryImages.length,
    )
  }

  if (isLoading) {
    return (
      <div className="room-detail-container">
        <div className="room-detail-skeleton">
          <div className="skeleton-bar" style={{ width: '140px', height: '36px', marginBottom: '20px' }} />
          <div className="skeleton-hero" />
          <div className="skeleton-bar" style={{ width: '60%', height: '40px', marginTop: '24px' }} />
          <div className="skeleton-bar" style={{ width: '40%', height: '24px', marginTop: '12px' }} />
        </div>
      </div>
    )
  }

  // Edge case 3: Room not found or invalid
  if (error || !roomType) {
    return (
      <div className="room-detail-container">
        <nav className="room-detail-nav">
          <button
            type="button"
            className="room-detail-back-btn"
            onClick={handleBackToList}
            id="back-to-list-error-btn"
          >
            ← Quay lại danh sách loại phòng
          </button>
        </nav>

        <div className="room-not-found-card" role="alert">
          <div className="not-found-icon">🏚️</div>
          <h2>Không tìm thấy thông tin loại phòng</h2>
          <p className="not-found-desc">
            {error || 'Loại phòng bạn đang tìm kiếm không tồn tại hoặc đã ngừng phục vụ.'}
          </p>
          <div className="not-found-actions">
            <button
              type="button"
              className="btn-primary"
              onClick={handleBackToList}
              id="return-catalog-btn"
            >
              Xem các loại phòng khác
            </button>
          </div>
        </div>
      </div>
    )
  }

  // Stock status badge configuration
  const isOutOfStock = roomType.availableRooms <= 0
  const isLowStock = roomType.availableRooms > 0 && roomType.availableRooms <= 2

  return (
    <div className="room-detail-page">
      {/* Top Header & Navigation */}
      <header className="room-detail-header-bar">
        <div className="header-inner">
          <button
            type="button"
            className="room-detail-back-btn"
            onClick={handleBackToList}
            id="back-to-room-list-btn"
          >
            ← Quay lại danh sách loại phòng
          </button>

          <div className="header-breadcrumbs">
            <span>Trang chủ</span>
            <span className="separator">/</span>
            <span onClick={handleBackToList} style={{ cursor: 'pointer' }}>
              Danh sách phòng
            </span>
            <span className="separator">/</span>
            <span className="current">{roomType.name}</span>
          </div>
        </div>
      </header>

      <main className="room-detail-main">
        <div className="room-detail-container">
          {/* Main Title Banner */}
          <div className="room-detail-title-section">
            <div className="title-row">
              <div className="title-left">
                <span className="room-code-tag">Mã: {roomType.code}</span>
                <h1 className="room-name" id="room-detail-title">
                  {roomType.name}
                </h1>
              </div>

              <div className="title-right">
                <div
                  className={`stock-badge ${
                    isOutOfStock
                      ? 'out-of-stock'
                      : isLowStock
                      ? 'low-stock'
                      : 'in-stock'
                  }`}
                  id="room-stock-badge"
                >
                  <span className="dot" />
                  {isOutOfStock ? (
                    <strong>Đã hết phòng trống</strong>
                  ) : (
                    <>
                      Còn <strong>{roomType.availableRooms}</strong> phòng trống
                    </>
                  )}
                </div>
              </div>
            </div>
          </div>

          <div className="room-detail-grid">
            {/* Left Column: Gallery & Details */}
            <div className="room-detail-content">
              {/* Photo Gallery */}
              <section className="room-gallery-section" aria-label="Bộ ảnh phòng">
                <div className="gallery-main-viewport">
                  <img
                    src={
                      imageErrorMap[activeImageIndex]
                        ? FALLBACK_IMAGE
                        : galleryImages[activeImageIndex]
                    }
                    alt={`${roomType.name} - Ảnh ${activeImageIndex + 1}`}
                    className="gallery-main-img"
                    onError={() =>
                      setImageErrorMap((prev) => ({
                        ...prev,
                        [activeImageIndex]: true,
                      }))
                    }
                    id="room-main-image"
                  />

                  {galleryImages.length > 1 && (
                    <>
                      <button
                        type="button"
                        className="gallery-nav-btn prev"
                        onClick={prevImage}
                        aria-label="Ảnh trước"
                        id="gallery-prev-btn"
                      >
                        ‹
                      </button>
                      <button
                        type="button"
                        className="gallery-nav-btn next"
                        onClick={nextImage}
                        aria-label="Ảnh tiếp theo"
                        id="gallery-next-btn"
                      >
                        ›
                      </button>
                      <div className="gallery-counter">
                        📷 {activeImageIndex + 1} / {galleryImages.length}
                      </div>
                    </>
                  )}
                </div>

                {/* Thumbnail strip */}
                {galleryImages.length > 1 && (
                  <div className="gallery-thumbs-ribbon" role="tablist">
                    {galleryImages.map((img, idx) => (
                      <button
                        key={idx}
                        type="button"
                        role="tab"
                        aria-selected={idx === activeImageIndex}
                        className={`gallery-thumb-item ${
                          idx === activeImageIndex ? 'active' : ''
                        }`}
                        onClick={() => setActiveImageIndex(idx)}
                        id={`gallery-thumb-${idx}`}
                      >
                        <img
                          src={imageErrorMap[idx] ? FALLBACK_IMAGE : img}
                          alt={`Thumbnail ${idx + 1}`}
                        />
                      </button>
                    ))}
                  </div>
                )}
              </section>

              {/* Key Specs Card (Capacity, Beds, Stock) */}
              <section className="room-specs-card">
                <div className="spec-item" id="spec-capacity">
                  <div className="spec-icon">👥</div>
                  <div className="spec-info">
                    <span className="spec-label">Sức chứa tối đa</span>
                    <strong className="spec-value">
                      Tối đa {roomType.maxCapacity} khách
                    </strong>
                    <span className="spec-sub">
                      Tiêu chuẩn: {roomType.standardCapacity} người lớn, tối đa {roomType.maxCapacity} người
                    </span>
                  </div>
                </div>

                <div className="spec-divider" />

                <div className="spec-item" id="spec-beds">
                  <div className="spec-icon">🛏️</div>
                  <div className="spec-info">
                    <span className="spec-label">Bố trí giường</span>
                    <strong className="spec-value">
                      {roomType.numberOfBeds} giường
                    </strong>
                    <span className="spec-sub">Đệm cao cấp & drap tiêu chuẩn</span>
                  </div>
                </div>

                <div className="spec-divider" />

                <div className="spec-item" id="spec-stock">
                  <div className="spec-icon">🔑</div>
                  <div className="spec-info">
                    <span className="spec-label">Số lượng phòng còn trống</span>
                    <strong
                      className={`spec-value ${
                        isOutOfStock ? 'text-danger' : 'text-success'
                      }`}
                    >
                      {roomType.availableRooms > 0
                        ? `${roomType.availableRooms} phòng khả dụng`
                        : 'Tạm hết phòng'}
                    </strong>
                    <span className="spec-sub">
                      Cập nhật theo thời gian thực
                    </span>
                  </div>
                </div>
              </section>

              {/* Detailed Description */}
              <section className="room-section room-description-section">
                <h2 className="section-title">Mô tả chi tiết loại phòng</h2>
                <div className="description-content" id="room-description-text">
                  {roomType.description ? (
                    <p>{roomType.description}</p>
                  ) : (
                    <p className="text-muted">
                      Loại phòng tiện nghi, được bài trí tinh tế và trang bị đầy
                      đủ đồ dùng cần thiết mang lại trải nghiệm nghỉ dưỡng thoải
                      mái, ấm cúng như ở nhà.
                    </p>
                  )}
                </div>
              </section>

              {/* Amenities List */}
              <section className="room-section room-amenities-section">
                <h2 className="section-title">Tiện nghi đi kèm trong phòng</h2>
                {roomType.amenities && roomType.amenities.length > 0 ? (
                  <div className="amenities-grid" id="room-amenities-list">
                    {roomType.amenities.map((amenity) => (
                      <div key={amenity.id} className="amenity-badge">
                        <span className="amenity-icon">{amenity.icon || '✨'}</span>
                        <span className="amenity-name">{amenity.name}</span>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-muted">Phòng được trang bị tiện nghi cơ bản.</p>
                )}
              </section>
            </div>

            {/* Right Column: Pricing & Booking Card */}
            <aside className="room-booking-sidebar">
              <div className="booking-sticky-card">
                <div className="card-header">
                  <span className="price-tag-label">Giá lưu trú</span>
                  <div className="price-row">
                    <span className="price-amount">
                      {roomType.weekdayPrice
                        ? `${money.format(roomType.weekdayPrice)} đ`
                        : 'Liên hệ'}
                    </span>
                    <span className="price-unit">/ đêm (ngày thường)</span>
                  </div>
                  {roomType.weekendPrice && (
                    <div className="price-weekend-sub">
                      Cuối tuần: {money.format(roomType.weekendPrice)} đ / đêm
                    </div>
                  )}
                </div>

                <div className="card-specs-summary">
                  <div className="summary-line">
                    <span>Sức chứa tối đa:</span>
                    <strong>{roomType.maxCapacity} người</strong>
                  </div>
                  <div className="summary-line">
                    <span>Số giường ngủ:</span>
                    <strong>{roomType.numberOfBeds} giường</strong>
                  </div>
                  <div className="summary-line">
                    <span>Phòng còn trống:</span>
                    <strong
                      className={
                        isOutOfStock
                          ? 'text-danger'
                          : isLowStock
                          ? 'text-warning'
                          : 'text-success'
                      }
                    >
                      {roomType.availableRooms > 0
                        ? `${roomType.availableRooms} phòng`
                        : 'Hết phòng'}
                    </strong>
                  </div>
                </div>

                <div className="card-actions">
                  <button
                    type="button"
                    className="book-now-button"
                    disabled={isOutOfStock}
                    onClick={() => handleBooking(roomType.id)}
                    id="book-room-now-btn"
                  >
                    {isOutOfStock ? 'Tạm thời hết phòng' : 'Đặt phòng này ngay'}
                  </button>

                  <button
                    type="button"
                    className="view-catalog-link-btn"
                    onClick={handleBackToList}
                    id="view-other-rooms-btn"
                  >
                    Xem các loại phòng khác
                  </button>
                </div>

                <div className="booking-perks">
                  <div className="perk-item">
                    <span className="perk-icon">✓</span>
                    <span>Xác nhận phòng nhanh chóng trong ngày</span>
                  </div>
                  <div className="perk-item">
                    <span className="perk-icon">✓</span>
                    <span>Hỗ trợ lễ tân 24/7 trong suốt kỳ nghỉ</span>
                  </div>
                </div>
              </div>
            </aside>
          </div>
        </div>
      </main>
    </div>
  )
}
