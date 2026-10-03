import { useEffect, useState } from 'react'
import { getPublicRoomTypesCatalog } from '../services/guestBookingService'
import type { PublicRoomTypeDetail } from '../types/roomDetail'
import './PublicRoomListPage.css'

type PublicRoomListPageProps = {
  onSelectRoomType?: (id: number) => void
}

const money = new Intl.NumberFormat('vi-VN')

const FALLBACK_IMAGE =
  'https://images.unsplash.com/photo-1590490360182-c33d57733427?auto=format&fit=crop&w=1200&q=80'

export function PublicRoomListPage({ onSelectRoomType }: PublicRoomListPageProps) {
  const [roomTypes, setRoomTypes] = useState<PublicRoomTypeDetail[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [searchTerm, setSearchTerm] = useState('')

  useEffect(() => {
    setIsLoading(true)
    setError(null)

    getPublicRoomTypesCatalog()
      .then((data) => {
        setRoomTypes(data)
        setIsLoading(false)
      })
      .catch((err: unknown) => {
        setError(
          err instanceof Error
            ? err.message
            : 'Không tải được danh sách loại phòng',
        )
        setIsLoading(false)
      })
  }, [])

  const handleOpenDetail = (id: number) => {
    if (onSelectRoomType) {
      onSelectRoomType(id)
    } else {
      window.location.href = `/phong/${id}`
    }
  }

  const handleBookNow = (id: number) => {
    window.location.href = `/dat-phong?roomTypeId=${id}`
  }

  const filteredRoomTypes = roomTypes.filter((rt) => {
    const term = searchTerm.toLowerCase().trim()
    if (!term) return true
    return (
      rt.name.toLowerCase().includes(term) ||
      rt.code.toLowerCase().includes(term) ||
      (rt.description && rt.description.toLowerCase().includes(term))
    )
  })

  return (
    <div className="public-rooms-page">
      {/* Top Navbar */}
      <nav className="public-nav-bar">
        <div className="nav-inner">
          <div className="brand-logo" onClick={() => (window.location.href = '/phong')}>
            <span className="brand-leaf">🌿</span>
            <span className="brand-name">HomeStay Retreat</span>
          </div>

          <div className="nav-links">
            <a href="/phong" className="nav-link active">
              Danh sách loại phòng
            </a>
            <a href="/dat-phong" className="nav-link">
              Đặt phòng trực tuyến
            </a>
            <a href="/" className="nav-link nav-link-login">
              Quản trị viên
            </a>
          </div>
        </div>
      </nav>

      {/* Hero Banner */}
      <header className="public-rooms-hero">
        <div className="hero-content">
          <span className="hero-eyebrow">Trải Nghiệm Nghỉ Dưỡng</span>
          <h1 className="hero-title">Khám Phá Các Loại Phòng Của Homestay</h1>
          <p className="hero-subtitle">
            Không gian yên bình, thiết kế ấm cúng kết hợp đầy đủ tiện nghi hiện
            đại. Hãy chọn không gian hoàn hảo cho kỳ nghỉ của bạn.
          </p>

          <div className="search-filter-box">
            <input
              type="text"
              placeholder="Tìm kiếm loại phòng theo tên, tiện nghi..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="search-input"
              id="room-catalog-search-input"
            />
            {searchTerm && (
              <button
                type="button"
                className="clear-search-btn"
                onClick={() => setSearchTerm('')}
              >
                ✕
              </button>
            )}
          </div>
        </div>
      </header>

      {/* Main Listing Section */}
      <main className="rooms-catalog-main">
        <div className="catalog-container">
          <div className="catalog-header-meta">
            <h2>
              Danh sách phòng đang phục vụ{' '}
              <span className="count-badge">({filteredRoomTypes.length})</span>
            </h2>
          </div>

          {isLoading ? (
            <div className="rooms-grid-skeleton">
              {[1, 2, 3].map((n) => (
                <div key={n} className="room-card-skeleton" />
              ))}
            </div>
          ) : error ? (
            <div className="catalog-error-box" role="alert">
              <p>⚠️ {error}</p>
              <button
                type="button"
                onClick={() => window.location.reload()}
                className="retry-btn"
              >
                Tải lại trang
              </button>
            </div>
          ) : filteredRoomTypes.length === 0 ? (
            <div className="empty-catalog-box">
              <p>Không tìm thấy loại phòng nào phù hợp với từ khóa của bạn.</p>
              <button
                type="button"
                onClick={() => setSearchTerm('')}
                className="btn-link"
              >
                Xóa bộ lọc tìm kiếm
              </button>
            </div>
          ) : (
            <div className="rooms-grid">
              {filteredRoomTypes.map((roomType) => {
                const coverImage =
                  roomType.images && roomType.images.length > 0
                    ? roomType.images[0]
                    : FALLBACK_IMAGE
                const isOutOfStock = roomType.availableRooms <= 0
                const isLowStock =
                  roomType.availableRooms > 0 && roomType.availableRooms <= 2

                return (
                  <article
                    key={roomType.id}
                    className="room-card"
                    id={`room-card-${roomType.id}`}
                  >
                    <div
                      className="room-card-media"
                      onClick={() => handleOpenDetail(roomType.id)}
                    >
                      <img
                        src={coverImage}
                        alt={roomType.name}
                        className="room-card-img"
                        loading="lazy"
                      />

                      <div
                        className={`card-stock-pill ${
                          isOutOfStock
                            ? 'out-of-stock'
                            : isLowStock
                            ? 'low-stock'
                            : 'in-stock'
                        }`}
                      >
                        <span className="dot" />
                        {isOutOfStock
                          ? 'Đã hết phòng'
                          : `Còn ${roomType.availableRooms} phòng`}
                      </div>

                      <div className="card-code-pill">{roomType.code}</div>
                    </div>

                    <div className="room-card-body">
                      <div className="card-header-line">
                        <h3
                          className="room-card-title"
                          onClick={() => handleOpenDetail(roomType.id)}
                        >
                          {roomType.name}
                        </h3>
                      </div>

                      <div className="room-card-specs">
                        <span className="spec-tag" title="Sức chứa tối đa">
                          👥 Tối đa {roomType.maxCapacity} khách
                        </span>
                        <span className="spec-tag" title="Số giường">
                          🛏️ {roomType.numberOfBeds} giường
                        </span>
                      </div>

                      <p className="room-card-desc">
                        {roomType.description ||
                          'Phòng tiện nghi, không gian thoáng mát ấm áp dành cho kỳ nghỉ dưỡng trọn vẹn.'}
                      </p>

                      {/* Amenities preview tags */}
                      {roomType.amenities && roomType.amenities.length > 0 && (
                        <div className="room-card-amenities">
                          {roomType.amenities.slice(0, 4).map((amenity) => (
                            <span key={amenity.id} className="amenity-chip">
                              {amenity.icon} {amenity.name}
                            </span>
                          ))}
                          {roomType.amenities.length > 4 && (
                            <span className="amenity-chip more">
                              +{roomType.amenities.length - 4} tiện nghi khác
                            </span>
                          )}
                        </div>
                      )}

                      <div className="room-card-footer">
                        <div className="price-box">
                          <span className="price-val">
                            {roomType.weekdayPrice
                              ? `${money.format(roomType.weekdayPrice)} đ`
                              : 'Liên hệ'}
                          </span>
                          <span className="price-label">/ đêm</span>
                        </div>

                        <div className="card-btn-group">
                          <button
                            type="button"
                            className="btn-view-detail"
                            onClick={() => handleOpenDetail(roomType.id)}
                            id={`view-detail-btn-${roomType.id}`}
                          >
                            Xem chi tiết
                          </button>
                          <button
                            type="button"
                            className="btn-book-quick"
                            disabled={isOutOfStock}
                            onClick={() => handleBookNow(roomType.id)}
                            id={`book-quick-btn-${roomType.id}`}
                          >
                            Đặt phòng
                          </button>
                        </div>
                      </div>
                    </div>
                  </article>
                )
              })}
            </div>
          )}
        </div>
      </main>
    </div>
  )
}
