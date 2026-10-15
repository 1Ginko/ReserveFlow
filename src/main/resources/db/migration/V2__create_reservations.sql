CREATE TABLE reservations
(
    reservation_id     UUID PRIMARY KEY,
    product_id         UUID        NOT NULL,
    quantity           INTEGER     NOT NULL,
    reservation_status VARCHAR(20) NOT NULL,
    expires_at         TIMESTAMPTZ NOT NULL,
    version            BIGINT      NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_reservations_product
        FOREIGN KEY (product_id)
            REFERENCES products (product_id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_reservations_quantity
        CHECK (quantity > 0),

    CONSTRAINT chk_reservations_status
        CHECK (reservation_status  IN ( 'ACTIVE', 'CONFIRMED', 'CANCELLED', 'EXPIRED' )),

    CONSTRAINT chk_reservations_expiration
        CHECK (expires_at > created_at)
);

CREATE INDEX idx_reservations_product_id
    ON reservations (product_id);

CREATE INDEX idx_reservations_active_expires_at
    ON reservations (expires_at)
    WHERE reservation_status = 'ACTIVE';