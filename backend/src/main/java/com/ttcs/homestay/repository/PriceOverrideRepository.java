package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.PriceOverride;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PriceOverrideRepository extends JpaRepository<PriceOverride, Long> {

    /** Danh sách giá đè, đợt sớm nhất trước; tải kèm loại phòng để hiện tên. */
    @EntityGraph(attributePaths = "roomType")
    List<PriceOverride> findAllByOrderByStartDateAscIdAsc();
}