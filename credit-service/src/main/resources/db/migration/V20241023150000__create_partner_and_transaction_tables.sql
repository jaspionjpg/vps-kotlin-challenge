-- Partner table: stores partner info and current credit balance
CREATE TABLE partner (
    id              VARCHAR(36)     NOT NULL,
    external_id     VARCHAR(255)    NOT NULL UNIQUE,
    name            VARCHAR(255)    NOT NULL,
    balance         NUMERIC(19, 4)  NOT NULL DEFAULT 0,
    version         BIGINT          NOT NULL DEFAULT 0,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id)
);

CREATE INDEX idx_partner_external_id ON partner (external_id);

-- Transaction table: stores all credit/debit operations
CREATE TABLE transaction (
    id              VARCHAR(36)     NOT NULL,
    partner_id      VARCHAR(36)     NOT NULL,
    type            VARCHAR(20)     NOT NULL,
    amount          NUMERIC(19, 4)  NOT NULL,
    description     VARCHAR(500)    NOT NULL,
    idempotency_key VARCHAR(255)    NOT NULL UNIQUE,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    failure_reason  VARCHAR(500),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (id),
    CONSTRAINT fk_transaction_partner FOREIGN KEY (partner_id) REFERENCES partner (id)
);

CREATE INDEX idx_transaction_partner_id ON transaction (partner_id);
CREATE INDEX idx_transaction_status ON transaction (status);
CREATE INDEX idx_transaction_idempotency_key ON transaction (idempotency_key);
CREATE INDEX idx_transaction_created_at ON transaction (created_at);
