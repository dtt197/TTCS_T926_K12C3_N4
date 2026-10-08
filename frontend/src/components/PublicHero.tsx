import './PublicHero.css'

type PublicHeroProps = {
  titleId: string
}

export function PublicHero({ titleId }: PublicHeroProps) {
  return (
    <section className="customer-hero" aria-labelledby={titleId}>
      <div className="customer-hero__copy">
        <p className="customer-eyebrow">HOMESTAY</p>
        <h1 className="customer-hero__title" id={titleId}>
          Tìm kỳ nghỉ<br />phù hợp với bạn
        </h1>
        <p className="customer-hero__description">
          Không gian ấm cúng, tiện nghi hiện đại, gần gũi thiên nhiên.
          Trải nghiệm homestay thoải mái và đáng nhớ.
        </p>
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
    </section>
  )
}
