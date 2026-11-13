
-- USERS TABLE
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    email VARCHAR(255) NOT NULL,

    full_name VARCHAR(150) NOT NULL,

    -- Nullable to support Google/OIDC-only accounts
    password_hash VARCHAR(255),

    role VARCHAR(20) NOT NULL DEFAULT 'USER',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_users_role
        CHECK (role IN ('USER', 'ADMIN'))
);

-- Enforce case-insensitive email uniqueness
CREATE UNIQUE INDEX idx_users_email_unique
    ON users (LOWER(email));


-- WALLETS TABLE
CREATE TABLE wallets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL,

    balance NUMERIC(19, 2) NOT NULL DEFAULT 0.00,

    currency VARCHAR(3) NOT NULL DEFAULT 'NGN',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_wallets_user
        FOREIGN KEY (user_id)
        REFERENCES users(id)
        ON DELETE RESTRICT,

    CONSTRAINT uq_wallets_user
        UNIQUE (user_id),

    CONSTRAINT chk_wallets_balance
        CHECK (balance >= 0),

    CONSTRAINT chk_wallets_currency
        CHECK (currency ~ '^[A-Z]{3}$')
);
