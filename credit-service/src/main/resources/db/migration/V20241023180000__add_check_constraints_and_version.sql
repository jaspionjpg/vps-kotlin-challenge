-- Financial invariants enforced at DB level
ALTER TABLE partner ADD CONSTRAINT chk_partner_balance_non_negative CHECK (balance >= 0);
ALTER TABLE transaction ADD CONSTRAINT chk_transaction_amount_positive CHECK (amount > 0);
ALTER TABLE transaction ADD CONSTRAINT chk_transaction_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'));
ALTER TABLE transaction ADD CONSTRAINT chk_transaction_type CHECK (type IN ('CREDIT', 'DEBIT'));

-- Optimistic locking version column for partner
ALTER TABLE partner ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
