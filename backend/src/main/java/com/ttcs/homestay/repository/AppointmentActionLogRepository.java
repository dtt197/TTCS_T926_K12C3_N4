package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.AppointmentActionLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppointmentActionLogRepository extends JpaRepository<AppointmentActionLog, Long> {
    List<AppointmentActionLog> findByAppointmentIdOrderByCreatedAtDesc(Long appointmentId);
}
