CREATE TABLE invoice_number_counters (
    invoice_year SMALLINT PRIMARY KEY,
    last_number INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT chk_invoice_number_counters_year CHECK (invoice_year BETWEEN 1 AND 9999),
    CONSTRAINT chk_invoice_number_counters_last_number CHECK (last_number BETWEEN 0 AND 999999)
);

CREATE TABLE issued_invoices (
    id BIGSERIAL PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    invoice_year SMALLINT NOT NULL,
    sequence_number INTEGER NOT NULL,
    invoice_number VARCHAR(11) NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    issued_by_user_id BIGINT NOT NULL,
    issued_by_name VARCHAR(150) NOT NULL,
    snapshot_json JSONB NOT NULL,
    pdf_bytes BYTEA NOT NULL,
    pdf_sha256 CHAR(64) NOT NULL,
    status VARCHAR(10) NOT NULL DEFAULT 'ISSUED',
    cancelled_at TIMESTAMPTZ,
    cancelled_by_user_id BIGINT,
    cancellation_reason VARCHAR(500),
    CONSTRAINT fk_issued_invoices_booking
        FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    CONSTRAINT fk_issued_invoices_issued_by_user
        FOREIGN KEY (issued_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_issued_invoices_cancelled_by_user
        FOREIGN KEY (cancelled_by_user_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_issued_invoices_booking UNIQUE (booking_id),
    CONSTRAINT uq_issued_invoices_year_sequence UNIQUE (invoice_year, sequence_number),
    CONSTRAINT uq_issued_invoices_number UNIQUE (invoice_number),
    CONSTRAINT chk_issued_invoices_year CHECK (invoice_year BETWEEN 1 AND 9999),
    CONSTRAINT chk_issued_invoices_sequence CHECK (sequence_number BETWEEN 1 AND 999999),
    CONSTRAINT chk_issued_invoices_number_format CHECK (
        invoice_number ~ '^[0-9]{4}-[0-9]{6}$'
    ),
    CONSTRAINT chk_issued_invoices_number_matches_parts CHECK (
        substring(invoice_number FROM 1 FOR 4) = to_char(invoice_year, 'FM0000')
        AND substring(invoice_number FROM 6 FOR 6) = to_char(sequence_number, 'FM000000')
    ),
    CONSTRAINT chk_issued_invoices_sha256 CHECK (pdf_sha256 ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_issued_invoices_status CHECK (status IN ('ISSUED', 'CANCELLED')),
    CONSTRAINT chk_issued_invoices_cancellation CHECK (
        (status = 'ISSUED' AND cancelled_at IS NULL AND cancelled_by_user_id IS NULL
            AND cancellation_reason IS NULL)
        OR (status = 'CANCELLED' AND cancelled_at IS NOT NULL AND cancelled_by_user_id IS NOT NULL
            AND NULLIF(BTRIM(cancellation_reason), '') IS NOT NULL)
    )
);
