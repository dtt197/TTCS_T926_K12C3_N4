import { useEffect, useState } from 'react'
import { checkNoShowUndoEligibility, undoBookingNoShow } from '../services/bookingService'
import type { NoShowUndoEligibility } from '../types/booking'
import { formatRemainingTime } from '../pages/bookingActionVisibility'
import './BookingNoShowUndoButton.css'

type BookingNoShowUndoButtonProps = {
  bookingId: number
  onSuccess?: () => void
  variant?: 'inline' | 'detail'
}

export function BookingNoShowUndoButton({
  bookingId,
  onSuccess,
  variant = 'inline',
}: BookingNoShowUndoButtonProps) {
  const [eligibility, setEligibility] = useState<NoShowUndoEligibility | null>(null)
  const [remainingSeconds, setRemainingSeconds] = useState<number>(0)
  const [loading, setLoading] = useState(true)
  const [undoing, setUndoing] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let isMounted = true
    checkNoShowUndoEligibility(bookingId)
      .then((data) => {
        if (isMounted) {
          setEligibility(data)
          setRemainingSeconds(data.remainingSeconds)
        }
      })
      .catch(() => {
        if (isMounted) {
          setEligibility(null)
        }
      })
      .finally(() => {
        if (isMounted) {
          setLoading(false)
        }
      })

    return () => {
      isMounted = false
    }
  }, [bookingId])

  useEffect(() => {
    if (remainingSeconds <= 0) return

    const timer = setInterval(() => {
      setRemainingSeconds((prev) => {
        if (prev <= 1) {
          clearInterval(timer)
          return 0
        }
        return prev - 1
      })
    }, 1000)

    return () => clearInterval(timer)
  }, [remainingSeconds])

  async function handleUndo() {
    if (undoing || remainingSeconds <= 0) return
    setUndoing(true)
    setError(null)
    try {
      await undoBookingNoShow(bookingId)
      if (onSuccess) {
        onSuccess()
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể hoàn tác thao tác không đến.')
    } finally {
      setUndoing(false)
    }
  }

  // Tự động ẩn khi hết thời gian 15 phút hoặc không đủ điều kiện
  if (!loading && (!eligibility || !eligibility.eligible || remainingSeconds <= 0)) {
    if (variant === 'detail') {
      return (
        <div className="no-show-undo-expired" role="status">
          <span>⏱ Thao tác không đến đã kết thúc (quá 15 phút, không thể hoàn tác).</span>
        </div>
      )
    }
    return null
  }

  if (loading) {
    return <span className="no-show-undo-loading">Đang kiểm tra...</span>
  }

  if (variant === 'detail') {
    return (
      <div className="no-show-undo-card" role="region" aria-label="Hoàn tác đánh dấu không đến">
        <div className="no-show-undo-info">
          <div className="no-show-undo-header">
            <span className="no-show-undo-icon" aria-hidden="true">⏱</span>
            <div>
              <strong>Lịch hẹn bị đánh dấu Không đến</strong>
              <p>
                Người thực hiện: <strong>{eligibility?.receptionistName || 'Lễ tân'}</strong>
                {eligibility?.markedAt && (
                  <span> lúc {new Date(eligibility.markedAt).toLocaleTimeString('vi-VN')}</span>
                )}
              </p>
            </div>
          </div>
          <div className="no-show-countdown-box">
            <span className="countdown-label">Thời gian hoàn tác còn lại:</span>
            <span className="countdown-clock" data-testid={`countdown-clock-${bookingId}`}>
              {formatRemainingTime(remainingSeconds)}
            </span>
          </div>
        </div>

        {error && <div className="no-show-undo-error" role="alert">{error}</div>}

        <button
          type="button"
          className="no-show-undo-btn detail"
          id={`undo-no-show-btn-${bookingId}`}
          data-testid={`undo-no-show-btn-${bookingId}`}
          disabled={undoing || remainingSeconds <= 0}
          onClick={handleUndo}
        >
          {undoing ? 'Đang hoàn tác...' : `↺ Hoàn tác đánh dấu không đến (${formatRemainingTime(remainingSeconds)})`}
        </button>
      </div>
    )
  }

  return (
    <div className="no-show-undo-inline">
      <button
        type="button"
        className="no-show-undo-btn inline"
        id={`undo-no-show-btn-${bookingId}`}
        data-testid={`undo-no-show-btn-${bookingId}`}
        title={`Người đánh dấu: ${eligibility?.receptionistName || 'Lễ tân'}. Bấm để hoàn tác trong vòng 15 phút.`}
        disabled={undoing || remainingSeconds <= 0}
        onClick={handleUndo}
      >
        <span aria-hidden="true">↺</span> {undoing ? '...' : `Hoàn tác (${formatRemainingTime(remainingSeconds)})`}
      </button>
      {error && <span className="no-show-undo-error-tooltip">{error}</span>}
    </div>
  )
}
