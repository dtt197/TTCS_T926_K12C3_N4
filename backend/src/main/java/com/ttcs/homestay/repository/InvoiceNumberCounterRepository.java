package com.ttcs.homestay.repository;

import com.ttcs.homestay.entity.InvoiceNumberCounter;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface InvoiceNumberCounterRepository extends JpaRepository<InvoiceNumberCounter, Short> {
    /** Creates the year row without racing another transaction that initializes the same year. */
    @Query(value = "insert into invoice_number_counters (invoice_year, last_number) values (:year, 0) "
            + "on conflict (invoice_year) do nothing", nativeQuery = true)
    @org.springframework.data.jpa.repository.Modifying
    int initializeYear(@Param("year") short year);

    /** Locks the existing year row until the surrounding transaction completes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select counter from InvoiceNumberCounter counter where counter.invoiceYear = :year")
    Optional<InvoiceNumberCounter> findByYearForUpdate(@Param("year") Short year);
}
