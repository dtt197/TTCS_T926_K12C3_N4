package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.Customer;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findFirstByPhone(String phone);
    Optional<Customer> findFirstByEmail(String email);
}
