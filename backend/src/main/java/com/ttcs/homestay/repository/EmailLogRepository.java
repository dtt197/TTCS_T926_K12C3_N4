package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.EmailLog;
import com.ttcs.homestay.entity.EmailStatus;
import com.ttcs.homestay.entity.EmailType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    List<EmailLog> findTop200ByEmailTypeOrderBySentAtDesc(EmailType emailType);

    // Kiểm tra booking đã có email loại này gửi thành công chưa.
    boolean existsByBookingIdAndEmailTypeAndStatus(
            Long bookingId,
            EmailType emailType,
            EmailStatus status);

    // Đếm số lần gửi có kết quả tương ứng để áp dụng giới hạn thử lại.
    long countByBookingIdAndEmailTypeAndStatus(
            Long bookingId,
            EmailType emailType,
            EmailStatus status);

    // Lấy kết quả gần nhất để hiển thị trạng thái nhắc của booking.
        Optional<EmailLog> findTopByBookingIdAndEmailTypeOrderBySentAtDescIdDesc(
            Long bookingId,
            EmailType emailType);

    Optional<EmailLog> findTopByBookingIdAndEmailTypeAndStatusOrderBySentAtDescIdDesc(
            Long bookingId,
            EmailType emailType,
            EmailStatus status);
}