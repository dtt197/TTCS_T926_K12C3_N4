package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.MaintenanceRequestHistory;
import java.util.List;
import org.springframework.data.repository.Repository;

// Deliberately exposes no update/delete operation for the processing history.
public interface MaintenanceRequestHistoryRepository extends Repository<MaintenanceRequestHistory, Long> {
    MaintenanceRequestHistory save(MaintenanceRequestHistory entry);
    List<MaintenanceRequestHistory> findByMaintenanceRequestIdOrderByOccurredAtAscIdAsc(Long requestId);
}
