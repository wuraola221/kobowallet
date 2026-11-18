CREATE OR REPLACE FUNCTION prevent_ledger_modification()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'Ledger entries are immutable. Create a reversal instead.';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entries_prevent_update
BEFORE UPDATE ON ledger_entries
FOR EACH ROW
EXECUTE FUNCTION prevent_ledger_modification();

CREATE TRIGGER trg_ledger_entries_prevent_delete
BEFORE DELETE ON ledger_entries
FOR EACH ROW
EXECUTE FUNCTION prevent_ledger_modification();

CREATE TRIGGER trg_ledger_entries_prevent_truncate
BEFORE TRUNCATE ON ledger_entries
FOR EACH STATEMENT
EXECUTE FUNCTION prevent_ledger_modification();


CREATE OR REPLACE FUNCTION validate_transfer_ledger()
RETURNS TRIGGER AS $$
DECLARE
    v_transfer_id UUID;
    v_transfer transfers%ROWTYPE;

    v_total_entries BIGINT;
    v_debit_entries BIGINT;
    v_credit_entries BIGINT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF TG_TABLE_NAME = 'transfers' THEN
            v_transfer_id := OLD.id;
        ELSE
            v_transfer_id := OLD.transfer_id;
        END IF;
    ELSE
        IF TG_TABLE_NAME = 'transfers' THEN
            v_transfer_id := NEW.id;
        ELSE
            v_transfer_id := NEW.transfer_id;
        END IF;
    END IF;

    SELECT *
    INTO v_transfer
    FROM transfers
    WHERE id = v_transfer_id;

    IF NOT FOUND THEN
        RETURN NULL;
    END IF;

    SELECT
        COUNT(*),
        COUNT(*) FILTER (WHERE entry_type = 'DEBIT'),
        COUNT(*) FILTER (WHERE entry_type = 'CREDIT')
    INTO
        v_total_entries,
        v_debit_entries,
        v_credit_entries
    FROM ledger_entries
    WHERE transfer_id = v_transfer_id;

    IF v_total_entries <> 2
       OR v_debit_entries <> 1
       OR v_credit_entries <> 1 THEN
        RAISE EXCEPTION
            'Transfer % must have exactly one DEBIT and one CREDIT entry.',
            v_transfer_id;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM ledger_entries
        WHERE transfer_id = v_transfer_id
          AND entry_type = 'DEBIT'
          AND wallet_id = v_transfer.sender_wallet_id
          AND amount = v_transfer.amount
          AND currency = v_transfer.currency
    ) OR NOT EXISTS (
        SELECT 1
        FROM ledger_entries
        WHERE transfer_id = v_transfer_id
          AND entry_type = 'CREDIT'
          AND wallet_id = v_transfer.recipient_wallet_id
          AND amount = v_transfer.amount
          AND currency = v_transfer.currency
    ) THEN
        RAISE EXCEPTION
            'Ledger entries do not match transfer %.',
            v_transfer_id;
    END IF;

    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_validate_transfer_ledger
AFTER INSERT OR UPDATE ON transfers
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION validate_transfer_ledger();


CREATE CONSTRAINT TRIGGER trg_validate_ledger_entries
AFTER INSERT OR UPDATE OR DELETE ON ledger_entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION validate_transfer_ledger();

ALTER TABLE wallets
    ADD CONSTRAINT chk_wallets_currency_ngn
    CHECK (currency = 'NGN');


ALTER TABLE transfers
    ADD CONSTRAINT chk_transfers_currency_ngn
    CHECK (currency = 'NGN');


ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_entries_currency_ngn
    CHECK (currency = 'NGN');
