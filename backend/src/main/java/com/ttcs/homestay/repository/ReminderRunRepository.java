package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.ReminderRun;
import java.time.LocalDate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderRunRepository extends JpaRepository<ReminderRun, Long> {

    Page<ReminderRun> findAllByOrderByStartedAtDescIdDesc(Pageable pageable);

    boolean existsByProcessingDateAndTriggerType(
            LocalDate processingDate,
            String triggerType);
                @org.springframework.data.jpa.repository.Lock(
            jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query(
            "select r from ReminderRun r where r.id = :id")
    java.util.Optional<ReminderRun> findByIdForUpdate(
     @org.springframework.data.repository.query.Param("id") Long id);
}