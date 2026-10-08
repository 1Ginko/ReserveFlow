CREATE TABLE products
(
    product_id          UUID PRIMARY KEY,
    sku                 VARCHAR(64)    NOT NULL,
    product_name        VARCHAR(120)   NOT NULL,
    product_description VARCHAR(1000),
    price               NUMERIC(19, 2) NOT NULL,
    currency_code       VARCHAR(3)     NOT NULL,
    active              BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_products_sku UNIQUE(sku),

    CONSTRAINT chk_products_price CHECK (price >= 0),

    CONSTRAINT chk_products_currency_code CHECK (currency_code ~ '^[A-Z]{3}$'));

CREATE TABLE product_inventory
(
    product_id        UUID PRIMARY KEY,
    on_hand_quantity  INTEGER     NOT NULL DEFAULT 0,
    reserved_quantity INTEGER     NOT NULL DEFAULT 0,
    version           BIGINT      NOT NULL DEFAULT 0,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_product_inventory_product
        FOREIGN KEY (product_id)
            REFERENCES products (product_id)
            ON DELETE RESTRICT,

    CONSTRAINT chk_product_inventory_on_hand  CHECK (on_hand_quantity >= 0),

    CONSTRAINT chk_product_inventory_reserved CHECK (reserved_quantity >= 0),

    CONSTRAINT chk_product_inventory_available CHECK (reserved_quantity <= on_hand_quantity)
);