ALTER TABLE reservations
    ADD COLUMN idempotency_key UUID;

ALTER TABLE reservations
    ADD CONSTRAINT uq_reservations_idempotency_key
        UNIQUE (idempotency_key);