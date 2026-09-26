package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	@EntityGraph(attributePaths = "role")
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<User> findByEmailIgnoreCase(String email);

	@EntityGraph(attributePaths = "role")
	Optional<User> findWithRoleById(Long id);
	boolean existsByEmailIgnoreCase(String email);

	@EntityGraph(attributePaths = "role")
	List<User> findAllByOrderByCreatedAtDesc();
}