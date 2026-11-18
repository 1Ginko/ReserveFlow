ALTER TABLE reservations
    DROP CONSTRAINT uq_reservations_idempotency_key;

ALTER TABLE reservations
    ADD CONSTRAINT uq_reservations_user_idempotency_key
        UNIQUE (user_id, idempotency_key);