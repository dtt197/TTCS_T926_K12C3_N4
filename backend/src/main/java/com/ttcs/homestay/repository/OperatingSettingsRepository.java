package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.OperatingSettings;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatingSettingsRepository extends JpaRepository<OperatingSettings, Long> {

    /** Phiên bản đang áp dụng = phiên bản mới nhất. */
    Optional<OperatingSettings> findFirstByOrderByCreatedAtDescIdDesc();

    /** AC4: lịch sử thay đổi, mới nhất trước. */
    List<OperatingSettings> findTop20ByOrderByCreatedAtDescIdDesc();

    /** AC4: phiên bản có hiệu lực tại một thời điểm (booking tạo lúc nào thì dùng tham số lúc đó). */
    Optional<OperatingSettings> findFirstByCreatedAtLessThanEqualOrderByCreatedAtDescIdDesc(OffsetDateTime at);
}