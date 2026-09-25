package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	@EntityGraph(attributePaths = "role")
	Optional<User> findByEmailIgnoreCase(String email);
}