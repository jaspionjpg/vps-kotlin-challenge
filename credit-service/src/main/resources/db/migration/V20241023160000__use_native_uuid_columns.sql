-- Switch id columns from VARCHAR(36) to native UUID with server-side generation

ALTER TABLE transaction DROP CONSTRAINT fk_transaction_partner;

ALTER TABLE partner
    ALTER COLUMN id SET DATA TYPE UUID USING id::uuid,
    ALTER COLUMN id SET DEFAULT gen_random_uuid();

ALTER TABLE transaction
    ALTER COLUMN id SET DATA TYPE UUID USING id::uuid,
    ALTER COLUMN id SET DEFAULT gen_random_uuid(),
    ALTER COLUMN partner_id SET DATA TYPE UUID USING partner_id::uuid;

ALTER TABLE transaction
    ADD CONSTRAINT fk_transaction_partner FOREIGN KEY (partner_id) REFERENCES partner (id);
