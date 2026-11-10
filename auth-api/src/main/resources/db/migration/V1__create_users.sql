CREATE TABLE users
(
    user_id       UUID PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    user_role     VARCHAR(20)  NOT NULL DEFAULT 'USER',
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_users_email UNIQUE (email),

    CONSTRAINT chk_users_role CHECK (user_role IN ('USER', 'ADMIN')),

    CONSTRAINT chk_users_email_normalized CHECK (email = lower(btrim(email)))
);