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
      // eslint-disable-next-line react-hooks/set-state-in-effect
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

              {/* S2-04: Thời gian nhận/trả phòng, chính sách trẻ nhỏ & phụ thu thêm người */}
              <section className="room-section room-policies-section" id="room-policies-section">
                <h2 className="section-title">Thời gian nhận phòng, trả phòng & Quy định lưu trú</h2>
                
                {/* Giờ nhận phòng & Giờ trả phòng */}
                <div className="check-times-grid">
                  <div className="check-time-card checkin-card" id="policy-checkin-card">
                    <div className="check-time-icon">🕒</div>
                    <div className="check-time-content">
                      <span className="check-time-label">Giờ nhận phòng (Check-in)</span>
                      <strong className="check-time-value" id="policy-checkin-time">
                        {roomType.checkInTime || '14:00'}
                      </strong>
                      <span className="check-time-hint">Từ {roomType.checkInTime || '14:00'} chiều</span>
                    </div>
                  </div>

                  <div className="check-time-card checkout-card" id="policy-checkout-card">
                    <div className="check-time-icon">🕛</div>
                    <div className="check-time-content">
                      <span className="check-time-label">Giờ trả phòng (Check-out)</span>
                      <strong className="check-time-value" id="policy-checkout-time">
                        {roomType.checkOutTime || '12:00'}
                      </strong>
                      <span className="check-time-hint">Trước {roomType.checkOutTime || '12:00'} trưa</span>
                    </div>
                  </div>
                </div>

                <div className="policies-detail-grid">
                  {/* Chính sách trẻ nhỏ */}
                  <div className="policy-block" id="policy-child-block">
                    <div className="policy-block-header">
                      <span className="policy-icon">👶</span>
                      <h3>Chính sách trẻ nhỏ</h3>
                    </div>
                    <div className="policy-block-body">
                      <div className="policy-status-pill">
                        <span
                          className={`policy-badge ${
                            roomType.allowChildren ? 'policy-badge-success' : 'policy-badge-danger'
                          }`}
                          id="policy-child-badge"
                        >
                          {roomType.allowChildren
                            ? '✓ Cho phép mang theo trẻ nhỏ'
                            : '✕ Không cho phép trẻ nhỏ'}
                        </span>
                      </div>
                      <p className="policy-desc" id="policy-child-text">
                        {roomType.childPolicy ||
                          (roomType.allowChildren
                            ? 'Cho phép mang theo trẻ nhỏ. Trẻ dưới 6 tuổi được miễn phí phụ thu khi ngủ chung giường với người lớn.'
                            : 'Loại phòng này không cho phép mang theo trẻ nhỏ để đảm bảo không gian yên tĩnh.')}
                      </p>
                    </div>
                  </div>

                  {/* Mức phụ thu thêm người */}
                  <div className="policy-block" id="policy-extra-guest-block">
                    <div className="policy-block-header">
                      <span className="policy-icon">👥</span>
                      <h3>Mức phụ thu thêm người</h3>
                    </div>
                    <div className="policy-block-body">
                      <div className="extra-fee-highlight" id="policy-extra-fee">
                        <span className="fee-amount">
                          +{money.format(roomType.extraPersonFee || 200000)} đ
                        </span>
                        <span className="fee-unit">/ người / đêm</span>
                      </div>
                      <p className="policy-desc" id="policy-extra-desc">
                        Sức chứa tiêu chuẩn: <strong>{roomType.standardCapacity} khách</strong>. Tối đa:{' '}
                        <strong>{roomType.maxCapacity} khách</strong>.
                        {roomType.maxCapacity > roomType.standardCapacity ? (
                          <> Phụ thu thêm {money.format(roomType.extraPersonFee || 200000)} đ cho mỗi khách vượt quá số lượng tiêu chuẩn {roomType.standardCapacity} người.</>
                        ) : (
                          <> Loại phòng này chỉ nhận tối đa đúng sức chứa tiêu chuẩn, không nhận thêm người.</>
                        )}
                      </p>
                    </div>
                  </div>
                </div>
              </section>

              {/* S2-04: Chính sách hủy phòng & Các mốc phí phạt */}
              <section className="room-section room-cancellation-section" id="room-cancellation-section">
                <div className="cancellation-header-row">
                  <div>
                    <h2 className="section-title">Chính sách hủy phòng & Mốc phí phạt</h2>
                    <p className="section-subtitle" id="policy-cancellation-summary">
                      {roomType.cancellationPolicy ||
                        'Chính sách hủy linh hoạt theo mốc thời gian áp dụng trước giờ nhận phòng.'}
                    </p>
                  </div>
                  <span className="cancellation-badge-secure">🛡️ Quy định minh bạch</span>
                </div>

                <div className="cancellation-tiers-container" id="cancellation-tiers-list">
                  {roomType.cancellationTiers && roomType.cancellationTiers.length > 0 ? (
                    <div className="cancellation-timeline">
                      {roomType.cancellationTiers.map((tier, idx) => {
                        const feeEstimated = Math.round(
                          ((roomType.weekdayPrice || 0) * tier.feePercent) / 100
                        )
                        const isFree = tier.feePercent === 0
                        const isFull = tier.feePercent === 100

                        return (
                          <div
                            key={idx}
                            className={`tier-card ${
                              isFree
                                ? 'tier-free'
                                : isFull
                                ? 'tier-full-penalty'
                                : 'tier-partial-penalty'
                            }`}
                            id={`cancellation-tier-${idx}`}
                          >
                            <div className="tier-timing">
                              <span className="tier-step">Mốc {idx + 1}</span>
                              <strong className="tier-time-label">{tier.timeLabel}</strong>
                            </div>

                            <div className="tier-refund-box">
                              <span className="tier-label">Tỷ lệ hoàn tiền:</span>
                              <strong className="tier-refund-percent">
                                {tier.refundPercent}% tiền cọc
                              </strong>
                            </div>

                            <div className="tier-fee-box">
                              <span className="tier-label">Mức phí phạt hủy:</span>
                              <span
                                className={`fee-pill ${
                                  isFree
                                    ? 'fee-free'
                                    : isFull
                                    ? 'fee-danger'
                                    : 'fee-warning'
                                }`}
                              >
                                {isFree
                                  ? 'Miễn phí hủy (0% phí)'
                                  : `Phạt ${tier.feePercent}% tiền cọc`}
                              </span>
                              {roomType.weekdayPrice > 0 && !isFree && (
                                <span className="tier-fee-sample">
                                  ~ {money.format(feeEstimated)} đ / đêm
                                </span>
                              )}
                            </div>

                            <div className="tier-detail-text">
                              <p>{tier.feeDescription}</p>
                            </div>
                          </div>
                        )
                      })}
                    </div>
                  ) : (
                    <div className="cancellation-tiers-fallback">
                      <p>Hủy trước 72 giờ: Miễn phí hủy (hoàn 100%). Hủy trước 24 giờ: Phí phạt 50%. Sát ngày: Phạt 100%.</p>
                    </div>
                  )}
                </div>
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
                    <span>Sức chứa:</span>
                    <strong>{roomType.standardCapacity} - {roomType.maxCapacity} người</strong>
                  </div>
                  <div className="summary-line">
                    <span>Giờ nhận phòng:</span>
                    <strong id="sidebar-checkin-time">{roomType.checkInTime || '14:00'}</strong>
                  </div>
                  <div className="summary-line">
                    <span>Giờ trả phòng:</span>
                    <strong id="sidebar-checkout-time">{roomType.checkOutTime || '12:00'}</strong>
                  </div>
                  <div className="summary-line">
                    <span>Trẻ nhỏ:</span>
                    <strong id="sidebar-child-policy">{roomType.allowChildren ? 'Cho phép' : 'Không nhận'}</strong>
                  </div>
                  <div className="summary-line">
                    <span>Phụ thu thêm khách:</span>
                    <strong id="sidebar-extra-fee">+{money.format(roomType.extraPersonFee || 200000)} đ</strong>
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
