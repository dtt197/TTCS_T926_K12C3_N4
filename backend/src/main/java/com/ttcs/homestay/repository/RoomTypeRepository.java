package com.ttcs.homestay.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ttcs.homestay.entity.RoomType;

@Repository
public interface RoomTypeRepository extends JpaRepository<RoomType, Long> {
  boolean existsByCode(String code);
}