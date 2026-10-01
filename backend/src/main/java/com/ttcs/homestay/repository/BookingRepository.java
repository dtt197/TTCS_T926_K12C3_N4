package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ttcs.homestay.entity.Booking;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findAllByOrderByCreatedAtDescIdDesc();
}