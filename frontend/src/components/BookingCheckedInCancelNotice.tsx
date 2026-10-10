import './BookingCheckedInCancelNotice.css'

/** S3-05 Lát 3: booking đã nhận phòng không huỷ được, chỉ lễ tân sang thủ tục trả phòng sớm. */
export function BookingCheckedInCancelNotice() {
  return (
    <p className="booking-checked-in-notice" role="note">
      Booking đã nhận phòng nên không huỷ được. Nếu khách rời đi sớm, hãy làm thủ tục trả phòng sớm.
    </p>
  )
}