CREATE UNIQUE INDEX IF NOT EXISTS uk_account_holds_active_transaction
ON account_holds(transaction_id)
WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_transactions_status_created
ON transactions(status, created_at);
