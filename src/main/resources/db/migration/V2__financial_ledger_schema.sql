CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_ref VARCHAR(100) NOT NULL,
    account_type VARCHAR(20) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_accounts_external_ref UNIQUE (external_ref),
    CONSTRAINT ck_accounts_type CHECK (account_type IN ('WALLET','FUNDING')),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE','FROZEN','CLOSED')),
    CONSTRAINT ck_accounts_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE TABLE transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_txn_id VARCHAR(100) NOT NULL,
    payer_account_id UUID NOT NULL REFERENCES accounts(id),
    payee_account_id UUID NOT NULL REFERENCES accounts(id),
    amount NUMERIC(18,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_transactions_external_txn_id UNIQUE (external_txn_id),
    CONSTRAINT ck_transactions_amount CHECK (amount > 0),
    CONSTRAINT ck_transactions_accounts CHECK (payer_account_id <> payee_account_id),
    CONSTRAINT ck_transactions_status CHECK (
        status IN ('INITIATED','AUTHORIZED','SETTLED','FAILED','EXPIRED','REVERSAL_REQUESTED','REVERSED')
    ),
    CONSTRAINT ck_transactions_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE TABLE account_balances (
    account_id UUID PRIMARY KEY REFERENCES accounts(id),
    committed_balance NUMERIC(18,4) NOT NULL DEFAULT 0,
    available_balance NUMERIC(18,4) NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_balances_committed_nonnegative CHECK (committed_balance >= 0),
    CONSTRAINT ck_balances_available_nonnegative CHECK (available_balance >= 0),
    CONSTRAINT ck_balances_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE TABLE account_holds (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    amount NUMERIC(18,4) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    settlement_ref UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_holds_amount CHECK (amount > 0),
    CONSTRAINT ck_holds_status CHECK (status IN ('ACTIVE','CONSUMED','RELEASED','EXPIRED')),
    CONSTRAINT ck_holds_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    posting_id UUID NOT NULL,
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    currency CHAR(3) NOT NULL,
    entry_type VARCHAR(10) NOT NULL,
    amount NUMERIC(18,4) NOT NULL,
    reason VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_ledger_entry_type CHECK (entry_type IN ('DEBIT','CREDIT')),
    CONSTRAINT ck_ledger_reason CHECK (reason IN ('SETTLEMENT','REVERSAL')),
    CONSTRAINT ck_ledger_amount CHECK (amount > 0),
    CONSTRAINT ck_ledger_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE TABLE transaction_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    event_type VARCHAR(60) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(200) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    response_status INTEGER,
    response_body JSONB,
    resource_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE outbox (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(60) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING','PUBLISHED','FAILED')),
    CONSTRAINT ck_outbox_attempts CHECK (attempt_count >= 0)
);

CREATE TABLE reconciliation_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ
);

CREATE TABLE reconciliation_discrepancies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    batch_id UUID NOT NULL REFERENCES reconciliation_batches(id),
    account_id UUID REFERENCES accounts(id),
    expected_balance NUMERIC(18,4) NOT NULL,
    actual_balance NUMERIC(18,4) NOT NULL,
    description VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_holds_transaction ON account_holds(transaction_id);
CREATE INDEX idx_holds_account_status ON account_holds(account_id, status);
CREATE INDEX idx_ledger_posting ON ledger_entries(posting_id);
CREATE INDEX idx_ledger_transaction ON ledger_entries(transaction_id);
CREATE INDEX idx_ledger_account_created ON ledger_entries(account_id, created_at);
CREATE INDEX idx_transaction_events_transaction ON transaction_events(transaction_id, created_at);
CREATE INDEX idx_outbox_status_created ON outbox(status, created_at);
CREATE INDEX idx_reconciliation_discrepancy_batch ON reconciliation_discrepancies(batch_id);

CREATE OR REPLACE FUNCTION enforce_double_entry()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    debit_total NUMERIC(18,4);
    credit_total NUMERIC(18,4);
    entry_count INTEGER;
BEGIN
    SELECT
        COUNT(*),
        COALESCE(SUM(CASE WHEN entry_type = 'DEBIT' THEN amount ELSE 0 END), 0),
        COALESCE(SUM(CASE WHEN entry_type = 'CREDIT' THEN amount ELSE 0 END), 0)
    INTO entry_count, debit_total, credit_total
    FROM ledger_entries
    WHERE posting_id = COALESCE(NEW.posting_id, OLD.posting_id)
      AND currency = COALESCE(NEW.currency, OLD.currency);

    IF entry_count < 2 OR debit_total <> credit_total THEN
        RAISE EXCEPTION 'Double-entry invariant violated for posting_id %', COALESCE(NEW.posting_id, OLD.posting_id);
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_ledger_double_entry
AFTER INSERT OR UPDATE OR DELETE ON ledger_entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION enforce_double_entry();
