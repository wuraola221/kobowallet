CREATE TABLE transfers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    sender_wallet_id UUID NOT NULL,
    recipient_wallet_id UUID NOT NULL,

    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,

    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_transfers_sender_wallet
        FOREIGN KEY (sender_wallet_id)
        REFERENCES wallets(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_transfers_recipient_wallet
        FOREIGN KEY (recipient_wallet_id)
        REFERENCES wallets(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_transfers_different_wallets
        CHECK (sender_wallet_id <> recipient_wallet_id),

    CONSTRAINT chk_transfers_amount
        CHECK (amount > 0),

    CONSTRAINT chk_transfers_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT uq_transfers_sender_idempotency
        UNIQUE (sender_wallet_id, idempotency_key),

    CONSTRAINT chk_transfers_idempotency_key
        CHECK (length(btrim(idempotency_key)) > 0),

    CONSTRAINT chk_transfers_request_hash
        CHECK (request_hash ~ '^[0-9a-f]{64}$')
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    transfer_id UUID NOT NULL,
    wallet_id UUID NOT NULL,

    entry_type VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_ledger_entries_transfer
        FOREIGN KEY (transfer_id)
        REFERENCES transfers(id)
        ON DELETE RESTRICT,

    CONSTRAINT fk_ledger_entries_wallet
        FOREIGN KEY (wallet_id)
        REFERENCES wallets(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_ledger_entries_type
        CHECK (entry_type IN ('DEBIT', 'CREDIT')),

    CONSTRAINT chk_ledger_entries_amount
        CHECK (amount > 0),

    CONSTRAINT chk_ledger_entries_currency
        CHECK (currency ~ '^[A-Z]{3}$'),

    CONSTRAINT uq_ledger_entries_transfer_type
        UNIQUE (transfer_id, entry_type)
);

CREATE INDEX idx_transfers_sender_created_at
    ON transfers (sender_wallet_id, created_at DESC);

CREATE INDEX idx_transfers_recipient_created_at
    ON transfers (recipient_wallet_id, created_at DESC);

CREATE INDEX idx_ledger_entries_wallet_created_at
    ON ledger_entries (wallet_id, created_at DESC);
