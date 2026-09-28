package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.Amenity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmenityRepository extends JpaRepository<Amenity, Long> {

    /** S1-08 AC1: mã tiện nghi duy nhất, không phân biệt hoa thường. */
    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    List<Amenity> findAllByOrderByCodeAsc();
}