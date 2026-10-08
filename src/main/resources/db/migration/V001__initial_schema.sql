-- A partner app's account; it holds one or more bank connections.
CREATE TABLE partner (
    id            UUID PRIMARY KEY,
    version       BIGINT       NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    enabled       BOOLEAN      NOT NULL
);

-- The partner's registration with one bank; all simulation data is keyed by it.
CREATE TABLE bank_connection (
    id                      UUID PRIMARY KEY,
    version                 BIGINT       NOT NULL,
    created_at              TIMESTAMPTZ  NOT NULL,
    updated_at              TIMESTAMPTZ  NOT NULL,
    partner_id              UUID         NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    bank                    VARCHAR(8)   NOT NULL,
    partner_service_id      VARCHAR(8)   NOT NULL UNIQUE,
    client_id               VARCHAR(64)  NOT NULL UNIQUE,
    client_secret           VARCHAR(128) NOT NULL,
    public_key_pem          TEXT,
    endpoint_base_url       VARCHAR(500),
    endpoint_client_id      VARCHAR(128),
    endpoint_client_secret  VARCHAR(256),
    token_ttl_seconds       INTEGER      NOT NULL CHECK (token_ttl_seconds > 0),
    diagnostic_mode         BOOLEAN      NOT NULL,
    key_registered_at       TIMESTAMPTZ,
    token_obtained_at       TIMESTAMPTZ,
    signed_call_at          TIMESTAMPTZ,
    va_created_at           TIMESTAMPTZ,
    endpoint_reachable_at   TIMESTAMPTZ,
    inquiry_answered_at     TIMESTAMPTZ,
    payment_acknowledged_at TIMESTAMPTZ,
    CHECK ((endpoint_base_url IS NULL AND endpoint_client_id IS NULL AND endpoint_client_secret IS NULL)
        OR (endpoint_base_url IS NOT NULL AND endpoint_client_id IS NOT NULL AND endpoint_client_secret IS NOT NULL))
);
CREATE INDEX bank_connection_partner_idx ON bank_connection (partner_id);

-- VA prefixes are handed out in order.
CREATE SEQUENCE partner_service_id_seq START WITH 10001 MAXVALUE 99999999;

CREATE TABLE access_token (
    id         UUID PRIMARY KEY,
    connection_id UUID      NOT NULL REFERENCES bank_connection (id) ON DELETE CASCADE,
    token      VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL,
    expires_at TIMESTAMPTZ  NOT NULL
);
CREATE INDEX access_token_connection_idx ON access_token (connection_id);

CREATE TABLE external_id (
    connection_id UUID        NOT NULL REFERENCES bank_connection (id) ON DELETE CASCADE,
    business_date DATE        NOT NULL,
    external_id   VARCHAR(36) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (connection_id, business_date, external_id)
);

CREATE TABLE virtual_account (
    id                    UUID PRIMARY KEY,
    version               BIGINT        NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL,
    updated_at            TIMESTAMPTZ   NOT NULL,
    connection_id         UUID          NOT NULL REFERENCES bank_connection (id) ON DELETE CASCADE,
    customer_no           VARCHAR(20)   NOT NULL,
    virtual_account_no    VARCHAR(28)   NOT NULL,
    virtual_account_name  VARCHAR(255)  NOT NULL,
    virtual_account_email VARCHAR(255),
    virtual_account_phone VARCHAR(30),
    trx_id                VARCHAR(64)   NOT NULL,
    total_amount          NUMERIC(18,2) NOT NULL,
    currency              VARCHAR(3)    NOT NULL,
    expired_date          TIMESTAMPTZ,
    status                VARCHAR(16)   NOT NULL,
    additional_json       TEXT,
    paid_at               TIMESTAMPTZ,
    UNIQUE (connection_id, trx_id)
);
CREATE UNIQUE INDEX virtual_account_active_no_idx ON virtual_account (connection_id, virtual_account_no)
    WHERE status = 'ACTIVE';

CREATE TABLE payment (
    id                  UUID PRIMARY KEY,
    version             BIGINT        NOT NULL,
    created_at          TIMESTAMPTZ   NOT NULL,
    updated_at          TIMESTAMPTZ   NOT NULL,
    connection_id       UUID          NOT NULL REFERENCES bank_connection (id) ON DELETE CASCADE,
    virtual_account_id  UUID          REFERENCES virtual_account (id) ON DELETE SET NULL,
    va_model            VARCHAR(16)   NOT NULL,
    virtual_account_no  VARCHAR(28)   NOT NULL,
    trx_id              VARCHAR(64),
    amount              NUMERIC(18,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    channel_id          VARCHAR(5)    NOT NULL,
    reference_no        VARCHAR(11),
    payment_request_id  VARCHAR(128)  NOT NULL,
    external_id         VARCHAR(36),
    notified_amount     NUMERIC(18,2),
    notification_status VARCHAR(16)   NOT NULL,
    notification_body   TEXT,
    paid_at             TIMESTAMPTZ   NOT NULL
);
CREATE INDEX payment_connection_idx ON payment (connection_id, paid_at);

CREATE TABLE ledger_entry (
    id                 UUID PRIMARY KEY,
    created_at         TIMESTAMPTZ   NOT NULL,
    connection_id      UUID          NOT NULL REFERENCES bank_connection (id) ON DELETE CASCADE,
    payment_id         UUID          REFERENCES payment (id) ON DELETE SET NULL,
    journal_id         VARCHAR(32)   NOT NULL UNIQUE,
    transaction_time   TIMESTAMPTZ   NOT NULL,
    entry_type         VARCHAR(8)    NOT NULL,
    amount             NUMERIC(18,2) NOT NULL,
    currency           VARCHAR(3)    NOT NULL,
    virtual_account_no VARCHAR(28)   NOT NULL,
    remark             VARCHAR(255)  NOT NULL
);
CREATE INDEX ledger_entry_connection_idx ON ledger_entry (connection_id, transaction_time);

CREATE TABLE exchange_log (
    id               UUID PRIMARY KEY,
    created_at       TIMESTAMPTZ  NOT NULL,
    connection_id    UUID         REFERENCES bank_connection (id) ON DELETE CASCADE,
    direction        VARCHAR(8)   NOT NULL,
    method           VARCHAR(8)   NOT NULL,
    url              VARCHAR(1000) NOT NULL,
    request_headers  TEXT         NOT NULL,
    request_body     TEXT,
    string_to_sign   TEXT,
    response_status  INTEGER,
    response_headers TEXT,
    response_body    TEXT,
    error            TEXT,
    duration_ms      BIGINT       NOT NULL
);
CREATE INDEX exchange_log_connection_idx ON exchange_log (connection_id, created_at DESC);

CREATE TABLE injection_rule (
    id          UUID PRIMARY KEY,
    version     BIGINT      NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    connection_id UUID      NOT NULL REFERENCES bank_connection (id) ON DELETE CASCADE,
    target      VARCHAR(64) NOT NULL,
    rule_type   VARCHAR(32) NOT NULL,
    delay_ms    BIGINT,
    http_status INTEGER,
    remaining   INTEGER     NOT NULL CHECK (remaining >= 0)
);
CREATE INDEX injection_rule_connection_idx ON injection_rule (connection_id, target);
