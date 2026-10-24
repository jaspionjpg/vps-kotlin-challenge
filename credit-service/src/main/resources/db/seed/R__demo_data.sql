-- Demo seed data for demonstration purposes
-- Activated via Spring profile: demo
-- Repeatable migration: re-applied whenever this file changes

-- Clear existing demo data (idempotent)
DELETE FROM transaction WHERE partner_id IN (
    SELECT id FROM partner WHERE external_id IN ('PARTNER-ACME', 'PARTNER-GLOBEX', 'PARTNER-INITECH')
);
DELETE FROM partner WHERE external_id IN ('PARTNER-ACME', 'PARTNER-GLOBEX', 'PARTNER-INITECH');

-- Demo partners
INSERT INTO partner (id, external_id, name, balance, created_at, updated_at) VALUES
    ('a1000000-0000-0000-0000-000000000001', 'PARTNER-ACME', 'Acme Corporation', 15000.0000, NOW() - INTERVAL '30 days', NOW()),
    ('a1000000-0000-0000-0000-000000000002', 'PARTNER-GLOBEX', 'Globex Industries', 8500.5000, NOW() - INTERVAL '20 days', NOW()),
    ('a1000000-0000-0000-0000-000000000003', 'PARTNER-INITECH', 'Initech Solutions', 0.0000, NOW() - INTERVAL '10 days', NOW());

-- Demo transactions for Acme Corporation
INSERT INTO transaction (id, partner_id, type, amount, description, idempotency_key, status, failure_reason, created_at, updated_at) VALUES
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000001', 'CREDIT', 10000.0000, 'Initial credit allocation', 'demo-acme-credit-1', 'COMPLETED', NULL, NOW() - INTERVAL '29 days', NOW() - INTERVAL '29 days'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000001', 'CREDIT', 5000.0000, 'Monthly top-up', 'demo-acme-credit-2', 'COMPLETED', NULL, NOW() - INTERVAL '15 days', NOW() - INTERVAL '15 days'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000001', 'DEBIT', 2500.0000, 'API usage charges - October', 'demo-acme-debit-1', 'COMPLETED', NULL, NOW() - INTERVAL '10 days', NOW() - INTERVAL '10 days'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000001', 'DEBIT', 750.0000, 'Premium feature access', 'demo-acme-debit-2', 'COMPLETED', NULL, NOW() - INTERVAL '5 days', NOW() - INTERVAL '5 days'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000001', 'CREDIT', 3250.0000, 'Bonus credit - partnership tier upgrade', 'demo-acme-credit-3', 'COMPLETED', NULL, NOW() - INTERVAL '2 days', NOW() - INTERVAL '2 days');

-- Demo transactions for Globex Industries
INSERT INTO transaction (id, partner_id, type, amount, description, idempotency_key, status, failure_reason, created_at, updated_at) VALUES
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000002', 'CREDIT', 20000.0000, 'Initial credit allocation', 'demo-globex-credit-1', 'COMPLETED', NULL, NOW() - INTERVAL '19 days', NOW() - INTERVAL '19 days'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000002', 'DEBIT', 8000.0000, 'Bulk API usage - Q3 batch processing', 'demo-globex-debit-1', 'COMPLETED', NULL, NOW() - INTERVAL '12 days', NOW() - INTERVAL '12 days'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000002', 'DEBIT', 3500.0000, 'Data export service', 'demo-globex-debit-2', 'COMPLETED', NULL, NOW() - INTERVAL '7 days', NOW() - INTERVAL '7 days'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000002', 'DEBIT', 15000.0000, 'Enterprise integration setup', 'demo-globex-debit-3', 'FAILED', 'Insufficient balance', NOW() - INTERVAL '3 days', NOW() - INTERVAL '3 days');

-- Demo transactions for Initech Solutions (new partner, no credits yet)
INSERT INTO transaction (id, partner_id, type, amount, description, idempotency_key, status, failure_reason, created_at, updated_at) VALUES
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000003', 'CREDIT', 5000.0000, 'Welcome credit package', 'demo-initech-credit-1', 'COMPLETED', NULL, NOW() - INTERVAL '9 days', NOW() - INTERVAL '9 days'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000003', 'DEBIT', 5000.0000, 'Full balance consumption - trial expired', 'demo-initech-debit-1', 'COMPLETED', NULL, NOW() - INTERVAL '4 days', NOW() - INTERVAL '4 days'),
    (gen_random_uuid(), 'a1000000-0000-0000-0000-000000000003', 'DEBIT', 1000.0000, 'Attempted usage after balance depleted', 'demo-initech-debit-2', 'FAILED', 'Insufficient balance', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');
