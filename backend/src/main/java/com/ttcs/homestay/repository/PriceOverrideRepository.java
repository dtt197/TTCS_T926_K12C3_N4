package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.PriceOverride;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceOverrideRepository extends JpaRepository<PriceOverride, Long> {

    /** Danh sách giá đè, đợt sớm nhất trước; tải kèm loại phòng để hiện tên. */
    @EntityGraph(attributePaths = "roomType")
    List<PriceOverride> findAllByOrderByStartDateAscIdAsc();

    /**
     * S2-02 Lát 2: các đợt giá đè của một loại phòng có ít nhất một đêm nằm trong [firstNight, lastNight].
     * Đợt cập nhật gần nhất đứng trước, để khi hai đợt trùng ngày thì luôn chọn cùng một đợt.
     */
    @Query("select p from PriceOverride p"
            + " where p.roomType.id = :roomTypeId"
            + " and p.startDate <= :lastNight and p.endDate >= :firstNight"
            + " order by p.updatedAt desc, p.id desc")
    List<PriceOverride> findOverlapping(
            @Param("roomTypeId") Long roomTypeId,
            @Param("firstNight") LocalDate firstNight,
            @Param("lastNight") LocalDate lastNight);
}