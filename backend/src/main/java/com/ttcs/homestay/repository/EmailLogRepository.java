package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.EmailLog;
import com.ttcs.homestay.entity.EmailType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    List<EmailLog> findTop200ByEmailTypeOrderBySentAtDesc(EmailType emailType);
}