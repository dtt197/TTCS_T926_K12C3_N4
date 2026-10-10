import { useEffect, useState } from 'react'
import {
  getBookingCancellationInfo,
  type BookingCancellationInfo,
} from '../services/bookingService'

/** Lý do chọn từ danh sách; nếu là chữ tự do (S3-02) thì hiển thị nguyên văn. */
const CANCEL_REASON_LABELS: Record<string, string> = {
  KHACH_DOI_KE_HOACH: 'Khách đổi kế hoạch',
  KHACH_KHONG_LIEN_LAC: 'Khách không liên lạc được',
  TRUNG_BOOKING: 'Trùng booking',
  LY_DO_KHAC: 'Lý do khác',
}

/** S3-05 Lát 2: người huỷ, thời điểm huỷ và lý do trong chi tiết booking đã huỷ. */
export function BookingCancellationInfoSection({ bookingId }: { bookingId: number }) {
  const [info, setInfo] = useState<BookingCancellationInfo | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let active = true
    getBookingCancellationInfo(bookingId)
      .then((data) => {
        if (active) setInfo(data)
      })
      .catch(() => {
        if (active) setFailed(true)
      })
    return () => {
      active = false
    }
  }, [bookingId])

  if (failed) {
    return (
      <p className="alert" role="alert">
        Không tải được thông tin huỷ booking.
      </p>
    )
  }
  if (info === null) {
    return <p>Đang tải thông tin huỷ booking...</p>
  }

  return (
    <>
      <h4>Thông tin huỷ booking</h4>
      <dl className="booking-detail-grid">
        <div>
          <dt>Lý do huỷ</dt>
          <dd>
            {info.cancelReason
              ? (CANCEL_REASON_LABELS[info.cancelReason] ?? info.cancelReason)
              : 'Không có'}
          </dd>
        </div>
        <div>
          <dt>Người huỷ</dt>
          <dd>{info.cancelledBy || 'Không có'}</dd>
        </div>
        <div>
          <dt>Thời điểm huỷ</dt>
          <dd>
            {info.cancelledAt
              ? new Date(info.cancelledAt).toLocaleString('vi-VN')
              : 'Không có'}
          </dd>
        </div>
      </dl>
    </>
  )
}