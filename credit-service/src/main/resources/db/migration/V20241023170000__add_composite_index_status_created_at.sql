-- Composite index for reconciliation query: WHERE status = ? AND created_at < ? ORDER BY created_at
CREATE INDEX idx_transaction_status_created_at ON transaction (status, created_at);
