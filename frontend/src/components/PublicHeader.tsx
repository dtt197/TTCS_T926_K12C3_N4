export function PublicHeader() {
  const pathname = window.location.pathname
  const isHome = pathname === '/trang-chu'
  const isRoomTypeList = pathname === '/danh-sach-loai-phong'
  return (
    <header className="customer-header">
      <div className="customer-header__inner">
        <a className="customer-brand" href="/trang-chu" aria-label="HomeStay - Trang chủ">
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
          <a
            className={`customer-nav__link${isHome ? ' customer-nav__link--active' : ''}`}
            href="/trang-chu"
            aria-current={isHome ? 'page' : undefined}
          >
            Trang chủ
          </a>
          <a
            className={`customer-nav__link${isRoomTypeList ? ' customer-nav__link--active' : ''}`}
            href="/danh-sach-loai-phong"
            aria-current={isRoomTypeList ? 'page' : undefined}
          >
            Các loại phòng
          </a>
          <a className="customer-nav__link" href="/tim-phong">Tra phòng trống</a>
          <a className="customer-nav__link" href="/tra-cuu-dat-phong">Tra cứu booking</a>
        </nav>
        <div className="customer-header__contact">
          <a href="tel:0389123456">0389 123 456</a>
          <small>Hỗ trợ 8:00 - 22:00</small>
        </div>
      </div>
    </header>
  )
}
