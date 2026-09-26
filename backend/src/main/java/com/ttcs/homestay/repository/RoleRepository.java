package com.ttcs.homestay.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ttcs.homestay.entity.Role;

public interface RoleRepository extends JpaRepository<Role, Long> {

	Optional<Role> findByCode(String code);
}