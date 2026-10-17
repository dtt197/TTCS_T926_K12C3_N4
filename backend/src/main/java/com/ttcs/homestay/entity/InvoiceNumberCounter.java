package com.ttcs.homestay.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Per-year row used as a transactional lock for invoice number allocation. */
@Entity
@Table(name = "invoice_number_counters")
@Getter
@Setter
@NoArgsConstructor
public class InvoiceNumberCounter {
    @Id
    @Column(name = "invoice_year", nullable = false)
    private Short invoiceYear;

    @Column(name = "last_number", nullable = false)
    private int lastNumber;
}
