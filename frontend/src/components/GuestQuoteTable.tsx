import { useEffect, useState } from 'react'
import { getGuestQuote } from '../services/guestBookingService'
import type { GuestQuote, QuoteNight } from '../types/guestBooking'

type GuestQuoteTableProps = {
  roomTypeId: string
  checkInDate: string
  checkOutDate: string
}

/** Kết quả gắn với key của lần hỏi giá, để không hiện nhầm kết quả của lựa chọn trước. */
type QuoteResult = { key: string; data?: GuestQuote; error?: string }

const money = new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 0 })
const WEEKDAYS = ['CN', 'T2', 'T3', 'T4', 'T5', 'T6', 'T7']

/** yyyy-MM-dd thành "T6 10/05". */
function formatNight(isoDate: string) {
  const [year, month, day] = isoDate.split('-').map(Number)
  const weekday = WEEKDAYS[new Date(year, month - 1, day).getDay()]
  return `${weekday} ${String(day).padStart(2, '0')}/${String(month).padStart(2, '0')}`
}

function labelClass(priceType: QuoteNight['priceType']) {
  if (priceType === 'OVERRIDE') return 'guest-quote-label override'
  if (priceType === 'WEEKEND') return 'guest-quote-label weekend'
  return 'guest-quote-label'
}

/** S2-06 Lát 1: bảng giá tạm tính từng đêm, tự cập nhật khi khách đổi loại phòng hoặc ngày. */
export function GuestQuoteTable({ roomTypeId, checkInDate, checkOutDate }: GuestQuoteTableProps) {
  // Ngày dạng yyyy-MM-dd nên so sánh chuỗi là đúng thứ tự thời gian.
  const ready = Boolean(roomTypeId && checkInDate && checkOutDate && checkOutDate > checkInDate)
  const key = ready ? `${roomTypeId}|${checkInDate}|${checkOutDate}` : null
  const [result, setResult] = useState<QuoteResult | null>(null)

  useEffect(() => {
    if (!key) return
    let cancelled = false
    // Đợi khách chọn xong 300ms rồi mới hỏi giá.
    const timer = window.setTimeout(() => {
      getGuestQuote(Number(roomTypeId), checkInDate, checkOutDate)
        .then((data) => {
          if (!cancelled) setResult({ key, data })
        })
        .catch((err: unknown) => {
          if (!cancelled) {
            const message = err instanceof TypeError
              ? 'Không kết nối được tới homestay, vui lòng thử lại sau'
              : err instanceof Error ? err.message : 'Không tính được giá tạm tính'
            setResult({ key, error: message })
          }
        })
    }, 300)
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [key, roomTypeId, checkInDate, checkOutDate])

  if (!key) {
    return (
      <p className="guest-quote-hint">
        Chọn loại phòng, ngày nhận và ngày trả phòng để xem giá tạm tính từng đêm.
      </p>
    )
  }

  const shown = result?.key === key ? result : null

  return (
    <section className="guest-quote" aria-live="polite">
      <h3>Giá tạm tính</h3>
      {!shown ? (
        <p className="guest-quote-hint">Đang tính giá...</p>
      ) : shown.error ? (
        <p className="error-message">{shown.error}</p>
      ) : shown.data ? (
        <div className="guest-quote-table-wrapper">
          <table className="guest-quote-table">
            <thead>
              <tr>
                <th>Đêm</th>
                <th>Loại giá</th>
                <th className="price">Giá một đêm</th>
              </tr>
            </thead>
            <tbody>
              {shown.data.nightlyPrices.map((night) => (
                <tr key={night.date}>
                  <td>{formatNight(night.date)}</td>
                  <td><span className={labelClass(night.priceType)}>{night.label}</span></td>
                  <td className="price">{money.format(night.price)} đ</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <td colSpan={2}>Tổng {shown.data.nights} đêm</td>
                <td className="price">{money.format(shown.data.totalAmount)} đ</td>
              </tr>
            </tfoot>
          </table>
        </div>
      ) : null}
    </section>
  )
}